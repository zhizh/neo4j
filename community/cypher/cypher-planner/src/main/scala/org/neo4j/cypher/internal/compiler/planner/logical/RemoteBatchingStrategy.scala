/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.planner.logical

import org.neo4j.configuration.GraphDatabaseInternalSettings.RemoteBatchPropertiesImplementation
import org.neo4j.cypher.internal.compiler.helpers.PropertyAccessHelper
import org.neo4j.cypher.internal.compiler.helpers.PropertyAccessHelper.ContextualPropertyAccess
import org.neo4j.cypher.internal.compiler.helpers.PropertyAccessHelper.PropertyAccess
import org.neo4j.cypher.internal.compiler.phases.PlannerContext
import org.neo4j.cypher.internal.compiler.planner.logical.steps.index.EntityIndexLeafPlanner.IndexCompatiblePredicate
import org.neo4j.cypher.internal.expressions.CachedProperty
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NODE_TYPE
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.expressions.RELATIONSHIP_TYPE
import org.neo4j.cypher.internal.ir.PlannerQuery
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.logical.plans.CachedProperties
import org.neo4j.cypher.internal.logical.plans.CanGetValue
import org.neo4j.cypher.internal.logical.plans.DoNotGetValue
import org.neo4j.cypher.internal.logical.plans.GetValue
import org.neo4j.cypher.internal.logical.plans.GetValueFromIndexBehavior
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.planner.spi.DatabaseMode.SHARDED
import org.neo4j.cypher.internal.planner.spi.IndexDescriptor
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUpWithRecorder
import org.neo4j.cypher.internal.util.symbols.CTNode
import org.neo4j.cypher.internal.util.symbols.CTRelationship

import scala.collection.mutable

sealed trait RemoteBatchingStrategy {

  def planBatchProperties(
    accessedProperties: Set[PropertyAccess],
    input: LogicalPlan,
    context: LogicalPlanningContext,
    cachedPropertiesRewritableExpressions: CachePropertiesRewritableExpressions
  ): RemoteBatchingResult

  def getValueFromIndexBehaviors(
    indexDescriptor: IndexDescriptor,
    propertyPredicates: Seq[IndexCompatiblePredicate],
    exactPredicatesCanGetValue: Boolean,
    contextualPropertyAccess: ContextualPropertyAccess,
    queryGraph: QueryGraph
  ): Seq[GetValueFromIndexBehavior]

  def planBatchPropertiesForSelections(
    queryGraph: QueryGraph,
    input: LogicalPlan,
    context: LogicalPlanningContext,
    predicatesToSolve: Set[Expression]
  ): RemoteBatchingResult
}

case class RemoteBatchingResult(
  rewrittenExpressionsWithCachedProperties: CachePropertiesRewritableExpressions,
  plan: LogicalPlan
)

case class CachePropertiesRewritableExpressions(
  selections: Set[Expression] = Set.empty,
  projections: Map[LogicalVariable, Expression] = Map.empty
) {

  def rewrite(rewriter: Rewriter): CachePropertiesRewritableExpressions = {
    val rewrittenSelections =
      selections.map(_.endoRewrite(rewriter)) // todo: pushdown predicates to remote batch properties
    val rewrittenProjections = projections.map {
      case (variable, projection) => variable -> projection.endoRewrite(rewriter)
    }
    CachePropertiesRewritableExpressions(rewrittenSelections, rewrittenProjections)
  }
}

object RemoteBatchingStrategy {

  def fromConfig(query: PlannerQuery, context: PlannerContext): RemoteBatchingStrategy = {
    if (
      query.readOnly
      && context.planContext.databaseMode == SHARDED
      && context.config.remoteBatchPropertiesImplementation() == RemoteBatchPropertiesImplementation.PLANNER
    )
      InPlannerRemoteBatching
    else
      SkipRemoteBatching
  }

  def defaultValue(): RemoteBatchingStrategy = SkipRemoteBatching

  private case object InPlannerRemoteBatching extends RemoteBatchingStrategy {

    override def planBatchProperties(
      accessedProperties: Set[PropertyAccess],
      input: LogicalPlan,
      context: LogicalPlanningContext,
      cachedPropertiesRewritableExpressions: CachePropertiesRewritableExpressions
    ): RemoteBatchingResult = {
      val previouslyCachedProperties =
        context.staticComponents.planningAttributes.cachedPropertiesPerPlan.get(input.id)
      val propertiesToFetchBuilder = Set.newBuilder[CachedProperty]
      val rewriter =
        cachedPropertiesRewriter(
          context,
          previouslyCachedProperties,
          toMap(accessedProperties),
          propertiesToFetchBuilder
        )
      val rewrittenExpressionsWithCachedProperties = cachedPropertiesRewritableExpressions.rewrite(rewriter)
      val propertiesToFetch = propertiesToFetchBuilder.result()
      val inputWithProperties = if (propertiesToFetch.nonEmpty)
        context.staticComponents.logicalPlanProducer.planRemoteBatchProperties(input, propertiesToFetch, context)
      else input
      RemoteBatchingResult(rewrittenExpressionsWithCachedProperties, inputWithProperties)
    }

    override def planBatchPropertiesForSelections(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      predicatesToSolve: Set[Expression]
    ): RemoteBatchingResult = {
      // we compute not only the predicates that will be solved by this selection
      val predicatesToBeSolvedLater =
        queryGraph.selections.flatPredicatesSet
          .filterNot(context.staticComponents.planningAttributes.solveds.get(
            input.id
          ).asSinglePlannerQuery.queryGraph.selections.contains(_))
          .filter(expr => expr.dependencies.intersect(input.availableSymbols).nonEmpty)
      val accessedPropertiesForPredicates =
        PropertyAccessHelper.findPropertyAccesses(predicatesToBeSolvedLater.toSeq)
      val accessedProperties =
        accessedPropertiesForPredicates ++ context.plannerState.contextualPropertyAccess.interestingOrder ++ context.plannerState.contextualPropertyAccess.horizon

      context.settings.remoteBatchPropertiesStrategy.planBatchProperties(
        accessedProperties,
        input,
        context,
        CachePropertiesRewritableExpressions(selections = predicatesToSolve)
      )
    }

    private def shouldGetPropertyValue(
      propertyPredicate: IndexCompatiblePredicate,
      propsAccessForPredsMap: PropertyAccessInPredicates,
      contextualPropertyAccess: ContextualPropertyAccess
    ): Boolean = {
      val propertyAccess = PropertyAccess(
        propertyPredicate.variable,
        propertyPredicate.propertyKeyName.name
      )
      propsAccessForPredsMap.propertyAccessInPredicatesOtherThat(
        propertyAccess,
        propertyPredicate.predicate
      ) || contextualPropertyAccess.horizon.contains(propertyAccess)
    }

    override def getValueFromIndexBehaviors(
      indexDescriptor: IndexDescriptor,
      propertyPredicates: Seq[IndexCompatiblePredicate],
      exactPredicatesCanGetValue: Boolean,
      contextualPropertyAccess: ContextualPropertyAccess,
      queryGraph: QueryGraph
    ): Seq[GetValueFromIndexBehavior] = {
      val propsAccessForPredsMap = propertyAccessesToPredicatesMap(queryGraph.selections.flatPredicatesSet)

      propertyPredicates.map {
        case predicate if predicate.predicateExactness.isExact && exactPredicatesCanGetValue =>
          if (shouldGetPropertyValue(predicate, propsAccessForPredsMap, contextualPropertyAccess))
            GetValue
          else DoNotGetValue
        case predicate =>
          indexDescriptor.valueCapability match {
            case DoNotGetValue => DoNotGetValue
            case _ =>
              if (shouldGetPropertyValue(predicate, propsAccessForPredsMap, contextualPropertyAccess))
                GetValue
              else DoNotGetValue
          }
      }
    }

    private def toMap(accessedProperties: Set[PropertyAccess]): Map[LogicalVariable, Set[PropertyKeyName]] =
      accessedProperties.map {
        accessedProperty =>
          accessedProperty.variable -> PropertyKeyName(accessedProperty.propertyName)(InputPosition.NONE)
      }.groupMap(_._1)(_._2)

    private def cachedPropertiesRewriter(
      context: LogicalPlanningContext,
      previouslyCachedProperties: CachedProperties,
      accessedProperties: Map[LogicalVariable, Set[PropertyKeyName]],
      propertiesToFetchBuilder: mutable.Builder[CachedProperty, Set[CachedProperty]]
    ) = {
      bottomUpWithRecorder.apply(
        rewriter = Rewriter.lift {
          case property @ Property(logicalVariable: LogicalVariable, propertyKeyName) =>
            previouslyCachedProperties.entries.get(logicalVariable) match {
              case Some(entry) =>
                CachedProperty(entry.originalEntity, logicalVariable, propertyKeyName, entry.entityType)(
                  property.position
                )
              case None =>
                val entityType = context.semanticTable.typeFor(logicalVariable)
                if (entityType.is(CTNode))
                  CachedProperty(logicalVariable, logicalVariable, propertyKeyName, NODE_TYPE)(property.position)
                else if (entityType.is(CTRelationship))
                  CachedProperty(logicalVariable, logicalVariable, propertyKeyName, RELATIONSHIP_TYPE)(
                    property.position
                  )
                else
                  property
            }
        },
        recorder = {
          case (_, cachedProperty: CachedProperty) =>
            val cachedPropertiesForEntity =
              previouslyCachedProperties
                .entries
                .get(cachedProperty.entityVariable)
                .map(_.properties)
                .getOrElse(Set.empty)
            if (!cachedPropertiesForEntity.contains(cachedProperty.propertyKey)) {
              val newProps = accessedProperties
                .getOrElse(cachedProperty.entityVariable, Set.empty)
                .incl(cachedProperty.propertyKey)
                .diff(cachedPropertiesForEntity)
                .view
                .map { propertyKeyName =>
                  cachedProperty.copy(propertyKey = propertyKeyName, knownToAccessStore = true)(InputPosition.NONE)
                }
              propertiesToFetchBuilder.addAll(newProps)
            }
          case _ => ()
        },
        cancellation = context.staticComponents.cancellationChecker
      )
    }

    private def propertyAccessesToPredicatesMap(predicates: Set[Expression]): PropertyAccessInPredicates = {
      val propertyAccessToPredicatesMap = predicates.flatMap {
        predicate =>
          PropertyAccessHelper
            .findPropertyAccesses(Seq(predicate))
            .map((_, predicate))
      }.groupMap(_._1)(_._2)

      PropertyAccessInPredicates(propertyAccessToPredicatesMap)
    }

    private case class PropertyAccessInPredicates(backingMap: Map[PropertyAccess, Set[Expression]]) extends AnyVal {

      def propertyAccessInPredicatesOtherThat(propertyAccess: PropertyAccess, inputPredicate: Expression): Boolean =
        backingMap.get(propertyAccess).exists(_.exists(expr => expr != inputPredicate))
    }
  }

  private case object SkipRemoteBatching extends RemoteBatchingStrategy {

    override def planBatchProperties(
      accessedProperties: Set[PropertyAccess],
      input: LogicalPlan,
      context: LogicalPlanningContext,
      cachedPropertiesRewritableExpressions: CachePropertiesRewritableExpressions
    ): RemoteBatchingResult = RemoteBatchingResult(cachedPropertiesRewritableExpressions, input)

    override def getValueFromIndexBehaviors(
      indexDescriptor: IndexDescriptor,
      propertyPredicates: Seq[IndexCompatiblePredicate],
      exactPredicatesCanGetValue: Boolean,
      contextualPropertyAccess: ContextualPropertyAccess,
      queryGraph: QueryGraph
    ): Seq[GetValueFromIndexBehavior] = propertyPredicates.map {
      case predicate if predicate.predicateExactness.isExact && exactPredicatesCanGetValue => CanGetValue
      case _ => indexDescriptor.valueCapability
    }

    override def planBatchPropertiesForSelections(
      queryGraph: QueryGraph,
      input: LogicalPlan,
      context: LogicalPlanningContext,
      predicatesToSolve: Set[Expression]
    ): RemoteBatchingResult =
      RemoteBatchingResult(CachePropertiesRewritableExpressions(selections = predicatesToSolve), input)
  }
}
