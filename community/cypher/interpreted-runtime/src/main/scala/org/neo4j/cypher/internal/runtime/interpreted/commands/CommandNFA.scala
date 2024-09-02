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
package org.neo4j.cypher.internal.runtime.interpreted.commands

import org.neo4j.cypher.internal.ast.semantics.SemanticTable
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.logical.plans.Expand.VariablePredicate
import org.neo4j.cypher.internal.logical.plans.NFA
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.ast.ExpressionVariable
import org.neo4j.cypher.internal.runtime.interpreted.commands
import org.neo4j.cypher.internal.runtime.interpreted.commands.CommandNFA.CommandPredicateFunction
import org.neo4j.cypher.internal.runtime.interpreted.commands.CommandNFA.RelationshipQualifiers
import org.neo4j.cypher.internal.runtime.interpreted.commands.CommandNFA.State
import org.neo4j.cypher.internal.runtime.interpreted.commands.convert.DirectionConverter.toGraphDb
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.cypher.internal.runtime.interpreted.pipes.RelationshipTypes
import org.neo4j.function.Predicates
import org.neo4j.internal.kernel.api.RelationshipDataReader
import org.neo4j.internal.kernel.api.helpers.traversal.SlotOrName
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.MultiRelationshipExpansion
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.NodeJuxtaposition
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.RelationshipExpansion
import org.neo4j.values.AnyValue
import org.neo4j.values.virtual.VirtualValues
import org.neo4j.values.virtual.VirtualValues.relationship

import java.util.function.LongPredicate
import java.util.function.Predicate

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class CommandNFA(
  states: Set[State],
  startState: State,
  finalState: State
) {

  /**
   * Compiles the CommandNFA into [[productgraph.State]]s.
   *
   * @param row row to compile the NFA for
   * @param queryState queryState to compile the NFA for
   * @return the start and end [[productgraph.State]] of the NFA.
   */
  def compile(row: CypherRow, queryState: QueryState): (productgraph.State, productgraph.State) = {

    def statePredicate(predicate: Option[CommandPredicateFunction]): LongPredicate =
      predicate.fold(Predicates.ALWAYS_TRUE_LONG) { predicate => l =>
        predicate(row, queryState, VirtualValues.node(l))
      }

    def relPredicate(qualifiers: RelationshipQualifiers): Predicate[RelationshipDataReader] =
      qualifiers.innerRelPred.fold(Predicates.alwaysTrue[RelationshipDataReader]()) { predicate => rel =>
        predicate(
          row,
          queryState,
          relationship(
            rel.relationshipReference(),
            rel.sourceNodeReference(),
            rel.targetNodeReference(),
            rel.`type`()
          )
        )
      }

    // This is then used to retrieve each state given the id, completing the transition -> targetState.id -> targetState
    // mapping
    val stateLookup = states.map(state =>
      state -> new productgraph.State(
        state.id,
        state.slotOrName,
        statePredicate(state.predicate),
        startState == state,
        finalState == state
      )
    ).toMap

    val reverseNJs = mutable.MultiDict.empty[productgraph.State, NodeJuxtaposition]
    val reverseREs = mutable.MultiDict.empty[productgraph.State, RelationshipExpansion]
    val reverseMREs = mutable.MultiDict.empty[productgraph.State, MultiRelationshipExpansion]

    for ((state, pgState) <- stateLookup) {
      pgState.setNodeJuxtapositions(
        state.nodeTransitions.map { transition =>
          val nj = new NodeJuxtaposition(stateLookup(state), stateLookup(transition.targetState))
          reverseNJs += (nj.targetState() -> nj)
          nj
        }.toArray
      )

      pgState.setRelationshipExpansions(
        state.relTransitions.map { transition =>
          val re = new RelationshipExpansion(
            stateLookup(state),
            relPredicate(transition.relationship),
            if (transition.relationship.types == null) null else transition.relationship.types.types(queryState.query),
            toGraphDb(transition.relationship.dir),
            transition.relationship.slotOrName,
            stateLookup(transition.targetState)
          )
          reverseREs += (re.targetState() -> re)
          re
        }.toArray
      )

      pgState.setMultiRelationshipExpansions(state.multiRelTransitions.map { transition =>
        val mre = new MultiRelationshipExpansion(
          stateLookup(state),
          transition.relationships.map(r =>
            new MultiRelationshipExpansion.Rel(
              relPredicate(r),
              if (r.types == null) null else r.types.types(queryState.query),
              toGraphDb(r.dir),
              r.slotOrName
            )
          ).toArray,
          transition.nodes.map(n =>
            new MultiRelationshipExpansion.Node(statePredicate(n.innerNodePred), n.slotOrName)
          ).toArray,
          stateLookup(transition.targetState)
        )
        reverseMREs += (mre.targetState() -> mre)
        mre
      }.toArray)
    }

    for (pgState <- reverseNJs.keySet) {
      pgState.setReverseNodeJuxtapositions(reverseNJs.get(pgState).toArray)
    }

    for (pgState <- reverseREs.keySet) {
      pgState.setReverseRelationshipExpansions(reverseREs.get(pgState).toArray)
    }

    for (pgState <- reverseMREs.keySet) {
      pgState.setReverseMultiRelationshipExpansions(reverseMREs.get(pgState).toArray)
    }

    (stateLookup(startState), stateLookup(finalState))
  }
}

object CommandNFA {

  private type CommandPredicateFunction = (CypherRow, QueryState, AnyValue) => Boolean

  class State(
    val id: Int,
    val slotOrName: SlotOrName,
    val predicate: Option[CommandPredicateFunction],
    var nodeTransitions: Seq[NodeJuxtapositionTransition],
    var relTransitions: Seq[RelationshipExpansionTransition],
    var multiRelTransitions: Seq[MultiRelationshipExpansionTransition]
  )

  case class NodeJuxtapositionTransition(
    targetState: State
  )

  case class NodeQualifiers(
    innerNodePred: Option[CommandPredicateFunction],
    slotOrName: SlotOrName
  )

  case class RelationshipQualifiers(
    innerRelPred: Option[CommandPredicateFunction],
    slotOrName: SlotOrName,
    types: RelationshipTypes,
    dir: SemanticDirection
  )

  case class RelationshipExpansionTransition(
    relationship: RelationshipQualifiers,
    targetState: State
  )

  object RelationshipExpansionTransition {

    def apply(
      innerRelPred: Option[CommandPredicateFunction],
      slotOrName: SlotOrName,
      types: RelationshipTypes,
      dir: SemanticDirection,
      targetState: State
    ): RelationshipExpansionTransition =
      new RelationshipExpansionTransition(RelationshipQualifiers(innerRelPred, slotOrName, types, dir), targetState)
  }

  case class MultiRelationshipExpansionTransition(
    relationships: Seq[RelationshipQualifiers],
    nodes: Seq[NodeQualifiers],
    targetState: State
  )

  def fromLogicalNFA(
    logicalNFA: NFA,
    predicateToCommand: VariablePredicate => commands.predicates.Predicate,
    getSlotOrName: LogicalVariable => SlotOrName = x => SlotOrName.None
  )(implicit st: SemanticTable): CommandNFA = {

    def convertPredicate(varPredicate: VariablePredicate): CommandPredicateFunction = {
      val predicate = predicateToCommand(varPredicate)
      val offset = ExpressionVariable.cast(varPredicate.variable).offset
      (row: CypherRow, state: QueryState, entity: AnyValue) => {
        state.expressionVariables(offset) = entity
        predicate.isTrue(row, state)
      }
    }

    def compileStubbedRelationshipExpansion(
      logicalPredicate: NFA.RelationshipExpansionPredicate,
      end: State
    )(implicit st: SemanticTable): RelationshipExpansionTransition = {
      val commandRelPred = logicalPredicate.relPred.map(convertPredicate)

      // In planner land, empty type seq means all types. We use null in runtime land to represent all types
      val types = logicalPredicate.types
      val relTypes = if (types.isEmpty) null else RelationshipTypes(types.toArray)

      RelationshipExpansionTransition(
        commandRelPred,
        getSlotOrName(logicalPredicate.relationshipVariable),
        relTypes,
        logicalPredicate.dir,
        end
      )
    }

    def compileStubbedMultiRelationshipExpansion(
      relPredicates: Seq[NFA.RelationshipExpansionPredicate],
      nodePredicates: Seq[NFA.NodeExpansionPredicate],
      end: State
    )(implicit st: SemanticTable): MultiRelationshipExpansionTransition = {
      val commandRelPreds = relPredicates.map(rp =>
        RelationshipQualifiers(
          rp.relPred.map(convertPredicate),
          getSlotOrName(rp.relationshipVariable),
          // In planner land, empty type seq means all types. We use null in runtime land to represent all types
          if (rp.types.isEmpty) null else RelationshipTypes(rp.types.toArray),
          rp.dir
        )
      )
      val commandNodePreds = nodePredicates.map(np =>
        NodeQualifiers(
          np.nodePred.map(convertPredicate),
          getSlotOrName(np.nodeVariable)
        )
      )
      MultiRelationshipExpansionTransition(
        commandRelPreds,
        commandNodePreds,
        end
      )
    }

    var startState: State = null
    var finalState: State = null

    // We need to compile the NFA in two phases here due to potential cycles in the NFA

    // first phase: create the states
    val stateLookup = logicalNFA.states.iterator.map { logicalState =>
      val commandState = new State(
        logicalState.id,
        getSlotOrName(logicalState.variable),
        logicalState.variablePredicate.map(convertPredicate),
        null,
        null,
        null
      )

      if (logicalNFA.startState == logicalState) {
        assert(startState == null, "There should only be one start state in an NFA")
        startState = commandState
      }
      if (logicalNFA.finalState == logicalState) {
        assert(finalState == null, "There should only be one final state in an NFA")
        finalState = commandState
      }

      logicalState.id -> commandState
    }.toMap

    // second phase: add the transitions
    for (logicalState <- logicalNFA.states) {
      val transitions = logicalNFA.transitions.getOrElse(logicalState.id, Seq.empty)

      val njs = ArrayBuffer.empty[NodeJuxtapositionTransition]
      val res = ArrayBuffer.empty[RelationshipExpansionTransition]
      val mres = ArrayBuffer.empty[MultiRelationshipExpansionTransition]
      transitions.foreach {
        case NFA.NodeJuxtapositionTransition(endId) =>
          val end = logicalNFA.states(endId)
          njs.append(NodeJuxtapositionTransition(stateLookup(end.id)))
        case NFA.RelationshipExpansionTransition(rp: NFA.RelationshipExpansionPredicate, endId) =>
          val end = logicalNFA.states(endId)
          res.append(compileStubbedRelationshipExpansion(rp, stateLookup(end.id)))
        case NFA.MultiRelationshipExpansionTransition(relPredicates, nodePredicates, endId) =>
          val end = logicalNFA.states(endId)
          mres.append(compileStubbedMultiRelationshipExpansion(relPredicates, nodePredicates, stateLookup(end.id)))
      }

      val commandState = stateLookup(logicalState.id)
      commandState.nodeTransitions = njs.toSeq
      commandState.relTransitions = res.toSeq
      commandState.multiRelTransitions = mres.toSeq
    }

    CommandNFA(
      states = stateLookup.values.toSet,
      startState,
      finalState
    )
  }

}
