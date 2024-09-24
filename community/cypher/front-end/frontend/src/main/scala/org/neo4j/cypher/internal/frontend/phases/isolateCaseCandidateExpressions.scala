/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.frontend.phases

import org.neo4j.cypher.internal.ast.AliasedReturnItem
import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.ProjectionClause
import org.neo4j.cypher.internal.ast.ReturnItem
import org.neo4j.cypher.internal.ast.ReturnItems
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.UnaliasedReturnItem
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.expressions.CaseExpression
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.IsAggregate
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.frontend.phases.factories.PlanPipelineTransformerFactory
import org.neo4j.cypher.internal.rewriting.conditions.SemanticInfoAvailable
import org.neo4j.cypher.internal.rewriting.rewriters.computeDependenciesForExpressions.ExpressionsHaveComputedDependencies
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Ref
import org.neo4j.cypher.internal.util.Rewritable.RewritableAny
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.Condition
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.topDown

case object CaseExpressionsAreIsolated extends Condition

/**
 * When we parse a CASE statement, we insert placeholders into predicate expressions like so:
 *
 * CASE myFunc()
 * WHEN IS NULL THEN 1 // IsNull(Placeholder)
 * WHEN < 0.5 THEN 2   // LessThan(Placeholder, 0.5)
 *
 * We want to replace these Placeholders with a variable reference in the [[replaceExtendedCasePlaceholders]] rewriter.
 * However, any expression can be the CASE EXPRESSION, so we first want to move out that to a preceding WITH in order
 * to reference a simple variable.
 *
 * RETURN CASE 1 + 4 WHEN 5 THEN true ...
 * 
 * Will be:
 * 
 * WITH 1 + 4 AS `  UNNAMED0`
 * RETURN CASE `  UNNAMED0` WHEN `  UNNAMED0` = 5 THEN true ...
 * */
case object isolateCaseCandidateExpressions extends StatementRewriter with StepSequencer.Step
    with PlanPipelineTransformerFactory {

  def preConditions: Set[StepSequencer.Condition] = Set(ExpressionsHaveComputedDependencies)
  override def postConditions: Set[Condition] = Set(CaseExpressionsAreIsolated)

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set(
    // Can introduces new ambiguous variable names itself.
    Namespacer.completed
  ) ++ SemanticInfoAvailable // Adds a WITH clause with no SemanticInfo

  override def instance(from: BaseState, context: BaseContext): Rewriter =
    bottomUp(rewriter(from.anonymousVariableNameGenerator), cancellation = context.cancellationChecker)

  private def rewriter(anonymousVariableNameGenerator: AnonymousVariableNameGenerator): Rewriter =
    Rewriter.lift {
      case q @ SingleQuery(clauses) if clauses.exists(clauseNeedingWork) =>
        val newClauses: Seq[Clause] = clauses.flatMap {
          case clause: ProjectionClause if clauseNeedingWork(clause) =>
            processClauses(clause, anonymousVariableNameGenerator)
          case clause => IndexedSeq(clause)
        }

        q.copy(clauses = newClauses)(q.position)
    }

  // As a case may be inside a case, we need to recursively rewrite them, as each inner case needs to be in a new WITH above.
  def processClauses(
    clause: ProjectionClause,
    anonymousVariableNameGenerator: AnonymousVariableNameGenerator
  ): Seq[Clause] = {
    val newClauses = getNewClauses(clause, anonymousVariableNameGenerator)

    if (clauseNeedingWork(newClauses.head)) {
      processClauses(newClauses.head.asInstanceOf[ProjectionClause], anonymousVariableNameGenerator) :+ newClauses.last
    } else {
      newClauses
    }
  }

  private def getNewClauses(
    clause: ProjectionClause,
    anonymousVariableNameGenerator: AnonymousVariableNameGenerator
  ): Seq[Clause] = {
    val clauseReturnItems = clause.returnItems.items
    // Split out CASE Expressions which contain a candidate that is an expression
    val (returnItemsWithCase, others) =
      clauseReturnItems.partition(r =>
        r.folder.treeExists {
          case c: CaseExpression => c.candidate match {
              case Some(_: Variable) => false
              case None              => false
              case _                 => true
            }
        }
      )

    val withCase = returnItemsWithCase.map(_.expression).toSet
    val otherVariables = others.collect {
      case a: AliasedReturnItem                => a.variable
      case UnaliasedReturnItem(v: Variable, _) => v
    }.toSet

    // Take the candidate expression and all dependencies from the CASE into the WITH,
    // as well as all non-problematic case expressions
    val withReturnItems: Set[ReturnItem] =
      withCase.folder.treeFold(Set.empty[Expression]) {
        case c: CaseExpression if c.candidate.isDefined && caseHasExpressionInsteadOfVariable(c.candidate) =>
          acc => SkipChildren(acc ++ Set(c.candidate.get) ++ c.scopeDependencies -- otherVariables)
      }.map {
        e =>
          AliasedReturnItem(e, Variable(anonymousVariableNameGenerator.nextName)(e.position))(e.position)
      } ++ others

    val withClause = With(
      distinct = false,
      ReturnItems(includeExisting = false, withReturnItems.toIndexedSeq)(clause.position),
      None,
      None,
      None,
      None
    )(clause.position)

    val expressionRewriter = createRewriterFor(withReturnItems)
    val newReturnItems = clauseReturnItems.map {
      case ri @ AliasedReturnItem(expression, _) =>
        ri.copy(expression = expression.endoRewrite(expressionRewriter))(
          ri.position
        )
      case ri @ UnaliasedReturnItem(expression, _) =>
        ri.copy(expression = expression.endoRewrite(expressionRewriter))(ri.position)
    }
    val resultClause = clause.withReturnItems(newReturnItems)

    IndexedSeq(withClause, resultClause)
  }

  private def caseHasExpressionInsteadOfVariable(e: Option[Expression]): Boolean = e match {
    case Some(_: Variable) => false
    case _                 => true
  }

  private def createRewriterFor(withReturnItems: Set[ReturnItem]): Rewriter = {
    val aliasedExpressionRefs: Map[Ref[Expression], LogicalVariable] =
      withReturnItems.map(ri => Ref(ri.expression) -> ri.alias.get).toMap
    lazy val aliasedExpressions: Map[Expression, LogicalVariable] =
      withReturnItems.map(ri => ri.expression -> ri.alias.get).toMap

    def inner = Rewriter.lift {
      case original: Expression =>
        aliasedExpressionRefs.get(Ref(original)).orElse {
          // Don't rewrite constant expressions, unless they were explicitly aliased.
          Option.when(isNotConstantExpression(original)) {
            aliasedExpressions.get(original)
          }.flatten
        }.map(_.copyId).getOrElse(original)
    }
    topDown(inner)
  }

  private def isNotConstantExpression(expr: Expression): Boolean =
    IsAggregate(expr) || expr.dependencies.nonEmpty

  private def clauseNeedingWork(c: Clause): Boolean = c.folder.treeExists {
    case c: CaseExpression => c.candidate match {
        case _ @Some(_: Variable) => false
        case _ @None              => false
        case _                    => true
      }
  }

  override def getTransformer(
    pushdownPropertyReads: Boolean,
    semanticFeatures: Seq[SemanticFeature]
  ): Transformer[BaseContext, BaseState, BaseState] = this
}
