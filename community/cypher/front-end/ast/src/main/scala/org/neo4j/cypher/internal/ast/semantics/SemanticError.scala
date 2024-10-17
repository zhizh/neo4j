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
package org.neo4j.cypher.internal.ast.semantics

import org.neo4j.cypher.internal.ast.UsingJoinHint
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CypherType
import org.neo4j.gqlstatus.ErrorGqlStatusObject
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation
import org.neo4j.gqlstatus.GqlHelper
import org.neo4j.gqlstatus.GqlParams
import org.neo4j.gqlstatus.GqlStatusInfoCodes

import scala.jdk.CollectionConverters.SeqHasAsJava

sealed trait SemanticErrorDef {
  def msg: String
  def position: InputPosition
  def withMsg(message: String): SemanticErrorDef
}

final case class SemanticError(gqlStatusObject: ErrorGqlStatusObject, msg: String, position: InputPosition)
    extends SemanticErrorDef {
  def this(msg: String, position: InputPosition) = this(null, msg, position)
  override def withMsg(message: String): SemanticError = copy(msg = message)
}

object SemanticError {

  def apply(msg: String, position: InputPosition): SemanticError = new SemanticError(null, msg, position)

  def unapply(errorDef: SemanticErrorDef): Option[(String, InputPosition)] = Some((errorDef.msg, errorDef.position))

  def invalidOption(schemaString: String, invalidOptions: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_22N04(
      invalidOptions,
      GqlParams.StringParam.input.process("OPTIONS"),
      java.util.List.of("indexProvider", "indexConfig"),
      position.line,
      position.column,
      position.offset
    )
    SemanticError(
      gql,
      s"Failed to create $schemaString: Invalid option provided, valid options are `indexProvider` and `indexConfig`.",
      position
    )
  }

  def authForbidsClauseError(
    provider: String,
    unsupportedClause: String,
    expected: java.util.List[String],
    position: InputPosition
  ): SemanticError = {
    val gql = GqlHelper.getGql42001_22N04(
      unsupportedClause,
      "auth provider " + GqlParams.StringParam.input.process(provider) + " attribute",
      expected,
      position.line,
      position.column,
      position.offset
    )
    SemanticError(
      gql,
      s"Auth provider `$provider` does not allow `$unsupportedClause` clause.",
      position
    )
  }

  def unsupportedActionAccess(
    actionName: String,
    expectedActions: java.util.List[String],
    position: InputPosition
  ): SemanticError = {
    val gql = GqlHelper.getGql42001_22N04(
      actionName,
      "property value access rules",
      expectedActions,
      position.line,
      position.column,
      position.offset
    )
    SemanticError(gql, s"$actionName is not supported for property value access rules.", position)
  }

  def yieldMissingColumn(
    originalName: String,
    expectedColumns: java.util.List[String],
    position: InputPosition
  ): SemanticError = {
    val gql = GqlHelper.getGql42001_22N04(
      originalName,
      "column name",
      expectedColumns,
      position.line,
      position.column,
      position.offset
    )
    SemanticError(gql, s"Trying to YIELD non-existing column: `$originalName`", position)
  }

  def invalidFunctionForIndex(
    entityIndexDescription: String,
    name: String,
    validFunction: String,
    position: InputPosition
  ): SemanticError = {
    val gql = GqlHelper.getGql42001_22N04(
      name,
      "function name",
      java.util.List.of(validFunction),
      position.line,
      position.column,
      position.offset
    )
    SemanticError(
      gql,
      s"Failed to create $entityIndexDescription: Function '$name' is not allowed, valid function is '$validFunction'.",
      position
    )
  }

  def invalidUseOfGraphFunction(graphFunction: String, pos: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(pos.line, pos.column, pos.offset)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N75)
        .atPosition(pos.line, pos.column, pos.offset)
        .withParam(GqlParams.StringParam.fun, graphFunction)
        .build())
      .build()
    SemanticError(
      gql,
      s"`$graphFunction` is only allowed at the first position of a USE clause.",
      pos
    )
  }
  val existsErrorMessage = "The EXISTS expression is not valid in driver settings."
  val countErrorMessage = "The COUNT expression is not valid in driver settings."
  val collectErrorMessage = "The COLLECT expression is not valid in driver settings."
  val genericErrorMessage = "This expression is not valid in driver settings."

  def existsInDriverSettings(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql22N81(
      GqlParams.StringParam.cmd.process("EXISTS"),
      "driver settings",
      position.line,
      position.column,
      position.offset
    )
    SemanticError(gql, existsErrorMessage, position)
  }

  def countInDriverSettings(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql22N81(
      GqlParams.StringParam.cmd.process("COUNT"),
      "driver settings",
      position.line,
      position.column,
      position.offset
    )
    SemanticError(gql, countErrorMessage, position)
  }

  def collectInDriverSettings(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql22N81(
      GqlParams.StringParam.cmd.process("COLLECT"),
      "driver settings",
      position.line,
      position.column,
      position.offset
    )
    SemanticError(gql, collectErrorMessage, position)
  }

  def genericDriverSettingsFail(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql22N81(
      GqlParams.StringParam.cmd.process("EXISTS"),
      "driver settings",
      position.line,
      position.column,
      position.offset
    )
    SemanticError(gql, genericErrorMessage, position)
  }

  def cannotUseJoinHint(hint: UsingJoinHint, prettifiedHint: String): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N76)
      .withParam(GqlParams.ListParam.hintList, Seq(prettifiedHint).asJava)
      .atPosition(hint.position.line, hint.position.column, hint.position.offset)
      .build()
    SemanticError(gql, "Cannot use join hint for single node pattern.", hint.position)
  }

  def legacyRelationShipDisjunction(
    sanitizedLabelExpression: String,
    labelExpression: String,
    containsIs: Boolean,
    isNode: Boolean = false,
    position: InputPosition
  ): SemanticError = {
    val isOrColon = if (containsIs) "IS " else ":"
    val msg = if (isNode) {
      s"""Label expressions are not allowed to contain '|:'.
         |If you want to express a disjunction of labels, please use `$isOrColon$sanitizedLabelExpression` instead""".stripMargin
    } else {
      s"""The semantics of using colon in the separation of alternative relationship types in conjunction with
         |the use of variable binding, inlined property predicates, or variable length is no longer supported.
         |Please separate the relationships types using `$isOrColon$sanitizedLabelExpression` instead.""".stripMargin
    }
    val gql = GqlHelper.getGql42001_42I20(
      if (isNode) "|:" else ":",
      labelExpression,
      position.line,
      position.column,
      position.offset
    )
    SemanticError(gql, msg, position)
  }

  def relationShipDisjunction(labelExpression: String, isNode: Boolean, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42I20(
      if (isNode) "|:" else ":",
      labelExpression,
      position.line,
      position.column,
      position.offset
    )
    SemanticError(
      gql,
      if (isNode) "Label expressions are not allowed to contain '|:'."
      else "Relationship types in a relationship type expressions may not be combined using ':'",
      position
    )
  }

  def unsupportedRequestOnSystemDatabase(
    invalidInput: String,
    legacyMessage: String,
    pos: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .withCause(
        ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N17)
          .withParam(GqlParams.StringParam.input, invalidInput)
          .build()
      )
      .build()
    SemanticError(
      gql,
      legacyMessage,
      pos
    )
  }

  def invalidInput(
    wrongInput: String,
    forField: String,
    expectedInput: List[String],
    legacyMessage: String,
    pos: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .withCause(
        ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N04)
          .withParam(GqlParams.StringParam.input, wrongInput)
          .withParam(GqlParams.StringParam.context, forField)
          .withParam(GqlParams.ListParam.inputList, java.util.List.of(expectedInput))
          .build()
      )
      .build()
    SemanticError(
      gql,
      legacyMessage,
      pos
    )
  }

  def propertyTypeUnsupportedInConstraint(
    constraintTypeDescription: String,
    originalPropertyType: CypherType
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
      .withParam(GqlParams.StringParam.constrDescrOrName, constraintTypeDescription)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
        .withParam(GqlParams.StringParam.item, originalPropertyType.description)
        .build())
      .build()
    new SemanticError(
      gql,
      s"Failed to create ${constraintTypeDescription} constraint: " +
        s"Invalid property type `${originalPropertyType.description}`.",
      originalPropertyType.position
    )
  }
}

sealed trait UnsupportedOpenCypher extends SemanticErrorDef

final case class FeatureError(
  gqlStatusObject: ErrorGqlStatusObject,
  msg: String,
  feature: SemanticFeature,
  position: InputPosition
) extends UnsupportedOpenCypher {

  def this(msg: String, featureError: SemanticFeature, position: InputPosition) =
    this(null, msg, featureError, position)
  override def withMsg(message: String): FeatureError = copy(msg = message)
}

object FeatureError {

  def apply(msg: String, featureError: SemanticFeature, position: InputPosition): FeatureError =
    new FeatureError(null, msg, featureError, position)

  def unapply(errorDef: FeatureError): Option[(String, SemanticFeature, InputPosition)] =
    Some((errorDef.msg, errorDef.feature, errorDef.position))
}
