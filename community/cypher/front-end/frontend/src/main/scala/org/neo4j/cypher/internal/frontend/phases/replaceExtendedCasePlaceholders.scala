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

import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.expressions.CaseExpression
import org.neo4j.cypher.internal.expressions.CaseExpression.Placeholder
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.UnPositionedVariable.varFor
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.frontend.phases.factories.PlanPipelineTransformerFactory
import org.neo4j.cypher.internal.rewriting.conditions.SemanticInfoAvailable
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.CancellationChecker
import org.neo4j.cypher.internal.util.Rewritable.RewritableAny
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.Condition
import org.neo4j.cypher.internal.util.topDown

case object CasePlaceholdersAreReplaced extends Condition

/**
 * When we parse a CASE statement, we insert placeholders into predicate expressions like so:
 *
 * CASE myFunc()
 * WHEN IS NULL THEN 1 // IsNull(Placeholder)
 * WHEN < 0.5 THEN 2   // LessThan(Placeholder, 0.5)
 *
 * This rewriter replaces the placeholders with variables, to be allocated as Expression Variables by the runtime.
 *
 * The reason this is not done directly in the parser is that we have to first rewrite the Case Expressions to 
 * have the candidate expression as a variable, and the variable declared in a preceding WITH. 
 * 
 * If the CASE expression is not in a WITH or a RETURN then it won't be rewritten in the aforementioned rewriter, 
 * so the placeholders are instead replaced with a new anonymous variable and this is referenced by the candidateVarName.
 * 
 * This rewriter must always run after [[isolateCaseCandidateExpressions]].
 * */
case object replaceExtendedCasePlaceholders extends StatementRewriter with StepSequencer.Step
    with PlanPipelineTransformerFactory {

  override def preConditions: Set[StepSequencer.Condition] = Set(
    // Otherwise it might rewrite ambiguous symbols incorrectly, e.g. when a grouping variable is shadowed in for-comprehension.
    Namespacer.completed,
    CaseExpressionsAreIsolated
  )
  override def postConditions: Set[Condition] = Set(CasePlaceholdersAreReplaced)
  override def invalidatedConditions: Set[StepSequencer.Condition] = SemanticInfoAvailable

  private def rewritePlaceholders(name: LogicalVariable): Rewriter =
    topDown(
      Rewriter.lift {
        case _: Placeholder => name
      },
      stopper = _.isInstanceOf[CaseExpression]
    )

  override def instance(from: BaseState, context: BaseContext): Rewriter =
    rewriter(from.anonymousVariableNameGenerator)(context.cancellationChecker)

  def rewriter(nameGen: AnonymousVariableNameGenerator)(cancellationChecker: CancellationChecker): Rewriter =
    topDown(
      Rewriter.lift {
        case c: CaseExpression if c.candidate.isDefined && c.alternatives.folder.treeExists {
            case _: Placeholder => true
          } =>
          c.candidate.get match {
            case v: Variable =>
              c.copy(
                alternatives = c.alternatives.map { case (predicate, result) =>
                  predicate.endoRewrite(rewritePlaceholders(v)) -> result
                }
              )(c.position)
            case _ =>
              val name = varFor(nameGen.nextName)

              c.copy(
                candidateVarName = Some(name),
                alternatives = c.alternatives.map { case (predicate, result) =>
                  predicate.endoRewrite(rewritePlaceholders(name)) -> result
                }
              )(c.position)
          }
      },
      cancellation = cancellationChecker
    )

  override def getTransformer(
    pushdownPropertyReads: Boolean,
    semanticFeatures: Seq[SemanticFeature]
  ): Transformer[BaseContext, BaseState, BaseState] = this

}
