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

import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.ExistsExpression
import org.neo4j.cypher.internal.frontend.helpers.TestState
import org.neo4j.cypher.internal.frontend.phases.rewriting.cnf.TestContext
import org.neo4j.cypher.internal.rewriting.RewriteTest
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.scalatest.Inside

class isolateCaseCandidateExpressionsTest extends CypherFunSuite with RewriteTest with AstConstructionTestSupport
    with Inside {

  def rewriterUnderTest: Rewriter =
    isolateCaseCandidateExpressions.instance(TestState(None), new TestContext(mock[Monitors]))

  def rewritePlaceholders: Rewriter =
    replaceExtendedCasePlaceholders.instance(TestState(None), new TestContext(mock[Monitors]))

  test("Should add WITH for CASE expression") {
    val input = singleQuery(
      return_(
        aliasedReturnItem(
          caseExpression(add(literal(2), literal(1)))
            .withExtendedCase(equals(_, literal(3)), literal(true))
            .withDefault(literal(false)),
          "value"
        )
      )
    )

    val output = input.endoRewrite(rewriterUnderTest).endoRewrite(rewritePlaceholders)

    val expected = singleQuery(
      with_(
        aliasedReturnItem(add(literal(2), literal(1)), "  UNNAMED0")
      ),
      return_(
        aliasedReturnItem(
          caseExpression(varFor("  UNNAMED0"))
            .withCase(equals(varFor("  UNNAMED0"), literal(3)), literal(true))
            .withDefault(literal(false)),
          "value"
        )
      )
    )

    output shouldBe expected
  }

  /**
   * UNWIND [['People', 10], ['People', 15], ['String', 1], ['String', 2]] as list
   * WITH list[0] as key, list[1] as value
   * WITH
   * key,
   * CASE key + 1 <---- will be rewritten to a variable in a WITH
   * WHEN 'People' then sum(value)
   * ELSE max(value)
   * END as value
   * RETURN *
   */
  test("Should add WITH for WITH containing CASE expression") {
    val input = singleQuery(
      unwind(
        listOf(
          listOf(literal("People"), literal(10)),
          listOf(literal("People"), literal(15)),
          listOf(literal("String"), literal(1)),
          listOf(literal("String"), literal(2))
        ),
        varFor("list")
      ),
      with_(
        aliasedReturnItem(containerIndex(varFor("list"), literal(0)), "key"),
        aliasedReturnItem(containerIndex(varFor("list"), literal(1)), "value")
      ),
      with_(
        aliasedReturnItem(varFor("key")),
        aliasedReturnItem(
          caseExpression(add(varFor("key"), literal(1)))
            .withExtendedCase(equals(_, literal("Person")), function("max", varFor("value")))
            .withDefault(function("max", varFor("value"))),
          "value"
        )
      ),
      returnAll
    )

    val output = input.endoRewrite(rewriterUnderTest).endoRewrite(rewritePlaceholders)

    val expected = singleQuery(
      unwind(
        listOf(
          listOf(literal("People"), literal(10)),
          listOf(literal("People"), literal(15)),
          listOf(literal("String"), literal(1)),
          listOf(literal("String"), literal(2))
        ),
        varFor("list")
      ),
      with_(
        aliasedReturnItem(containerIndex(varFor("list"), literal(0)), "key"),
        aliasedReturnItem(containerIndex(varFor("list"), literal(1)), "value")
      ),
      with_(
        aliasedReturnItem(add(varFor("key"), literal(1)), "  UNNAMED0"),
        aliasedReturnItem(varFor("value"), "  UNNAMED1"),
        aliasedReturnItem(varFor("key"))
      ),
      with_(
        aliasedReturnItem(varFor("key")),
        aliasedReturnItem(
          caseExpression(varFor("  UNNAMED0"))
            .withCase(equals(varFor("  UNNAMED0"), literal("Person")), function("max", varFor("  UNNAMED1")))
            .withDefault(function("max", varFor("  UNNAMED1"))),
          "value"
        )
      ),
      returnAll
    )

    output shouldBe expected
  }

  test("Should work correctly with Subquery expressions") {
    val input = singleQuery(
      return_(
        aliasedReturnItem(
          ExistsExpression(
            singleQuery(
              return_(
                aliasedReturnItem(
                  caseExpression(add(literal(2), literal(1)))
                    .withExtendedCase(equals(_, literal(3)), literal(true))
                    .withDefault(literal(false)),
                  "value"
                )
              )
            )
          )(pos, None, None),
          "value"
        )
      )
    )

    val output = input.endoRewrite(rewriterUnderTest).endoRewrite(rewritePlaceholders)

    val expected = singleQuery(
      return_(
        aliasedReturnItem(
          ExistsExpression(
            singleQuery(
              with_(
                aliasedReturnItem(add(literal(2), literal(1)), "  UNNAMED0")
              ),
              return_(
                aliasedReturnItem(
                  caseExpression(varFor("  UNNAMED0"))
                    .withCase(equals(varFor("  UNNAMED0"), literal(3)), literal(true))
                    .withDefault(literal(false)),
                  "value"
                )
              )
            )
          )(pos, None, None),
          "value"
        )
      )
    )

    output shouldBe expected
  }

  /** RETURN CASE (CASE 2 + 1 WHEN 3 THEN 2 ELSE 3 END) WHEN 3 THEN true ELSE false END as value
   * Should be rewritten to:
   * WITH 2 + 1 AS `  UNNAMED1`
   * WITH (CASE `  UNNAMED1` WHEN `  UNNAMED1` = 3 THEN 2 ELSE 3 END) AS `  UNNAMED0`
   * RETURN CASE `  UNNAMED0` WHEN `  UNNAMED0` = 3 THEN true ELSE false END as value
   */
  test("Should work with nested Case Expressions") {
    val innerCase = caseExpression(add(literal(2), literal(1)))
      .withExtendedCase(equals(_, literal(3)), literal(2))
      .withDefault(literal(3))

    val outerCase = singleQuery(
      return_(
        aliasedReturnItem(
          caseExpression(innerCase)
            .withExtendedCase(equals(_, literal(3)), literal(true))
            .withDefault(literal(false)),
          "value"
        )
      )
    )

    val output = outerCase.endoRewrite(rewriterUnderTest).endoRewrite(rewritePlaceholders)

    val expected = singleQuery(
      with_(
        aliasedReturnItem(add(literal(2), literal(1)), "  UNNAMED1")
      ),
      with_(
        aliasedReturnItem(
          caseExpression(varFor("  UNNAMED1"))
            .withCase(equals(varFor("  UNNAMED1"), literal(3)), literal(2))
            .withDefault(literal(3)),
          "  UNNAMED0"
        )
      ),
      return_(
        aliasedReturnItem(
          caseExpression(varFor("  UNNAMED0"))
            .withCase(equals(varFor("  UNNAMED0"), literal(3)), literal(true))
            .withDefault(literal(false)),
          "value"
        )
      )
    )

    output shouldBe expected
  }

  /** RETURN CASE (CASE (CASE 2 + 1 WHEN 3 THEN 2 ELSE 3 END) WHEN 5 THEN 7 ELSE 8 END) WHEN 3 THEN true ELSE false END as value
   * Should be rewritten to:
   * WITH 2 + 1 AS `  UNNAMED2`
   * WITH (CASE `  UNNAMED2` WHEN `  UNNAMED2` = 3 THEN 2 ELSE 3 END) AS `  UNNAMED1`
   * WITH (CASE `  UNNAMED1` WHEN `  UNNAMED1` = 5 THEN 7 ELSE 8 END) AS `  UNNAMED0`
   * RETURN CASE `  UNNAMED0` WHEN `  UNNAMED0` = 3 THEN true ELSE false END as value
   */
  test("Should work with extra nested Case Expressions") {
    val innerCase1 = caseExpression(add(literal(2), literal(1)))
      .withExtendedCase(equals(_, literal(3)), literal(2))
      .withDefault(literal(3))

    val innerCase2 = caseExpression(innerCase1)
      .withExtendedCase(equals(_, literal(5)), literal(7))
      .withDefault(literal(8))

    val outerCase = singleQuery(
      return_(
        aliasedReturnItem(
          caseExpression(innerCase2)
            .withExtendedCase(equals(_, literal(3)), literal(true))
            .withDefault(literal(false)),
          "value"
        )
      )
    )

    val output = outerCase.endoRewrite(rewriterUnderTest).endoRewrite(rewritePlaceholders)

    val expected = singleQuery(
      with_(
        aliasedReturnItem(add(literal(2), literal(1)), "  UNNAMED2")
      ),
      with_(
        aliasedReturnItem(
          caseExpression(varFor("  UNNAMED2"))
            .withCase(equals(varFor("  UNNAMED2"), literal(3)), literal(2))
            .withDefault(literal(3)),
          "  UNNAMED1"
        )
      ),
      with_(
        aliasedReturnItem(
          caseExpression(varFor("  UNNAMED1"))
            .withCase(equals(varFor("  UNNAMED1"), literal(5)), literal(7))
            .withDefault(literal(8)),
          "  UNNAMED0"
        )
      ),
      return_(
        aliasedReturnItem(
          caseExpression(varFor("  UNNAMED0"))
            .withCase(equals(varFor("  UNNAMED0"), literal(3)), literal(true))
            .withDefault(literal(false)),
          "value"
        )
      )
    )

    output shouldBe expected
  }

  // MATCH (n)-[r]->(m)
  // WITH (CASE n.age WHEN 36 THEN n ELSE m END).worksIn AS a
  // RETURN *
  test("Should work correctly with Case Expressions inside of other Expressions") {
    val input = singleQuery(
      match_(nodePat(name = Some("n"))),
      with_(
        aliasedReturnItem(
          prop(
            caseExpression(prop(varFor("n"), "age")).withExtendedCase(equals(_, literal(36)), varFor("n")),
            "worksIn"
          ),
          "a"
        )
      ),
      returnAll
    )

    val output = input.endoRewrite(rewriterUnderTest).endoRewrite(rewritePlaceholders)

    val expected = singleQuery(
      match_(nodePat(name = Some("n"))),
      with_(aliasedReturnItem(prop(varFor("n"), "age"), "  UNNAMED0"), aliasedReturnItem(varFor("n"), "  UNNAMED1")),
      with_(
        aliasedReturnItem(
          prop(
            caseExpression(varFor("  UNNAMED0")).withCase(
              equals(varFor("  UNNAMED0"), literal(36)),
              varFor("  UNNAMED1")
            ),
            "worksIn"
          ),
          "a"
        )
      ),
      returnAll
    )

    output shouldBe expected
  }

  test("does not rewrite things that should not be rewritten") {
    assertIsNotRewritten("RETURN CASE WHEN 1 = 1 THEN true ELSE false END AS value".stripMargin)
    assertIsNotRewritten("WITH 1 AS a RETURN CASE a WHEN 1 THEN true ELSE false END AS value".stripMargin)
  }
}
