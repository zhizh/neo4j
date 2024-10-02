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

import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.gqlstatus.ErrorClassification
import org.neo4j.gqlstatus.ErrorGqlStatusObject
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation
import org.neo4j.gqlstatus.GqlParams
import org.neo4j.gqlstatus.GqlStatusInfoCodes

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

  def invalidUseOfGraphFunction(graphFunction: String, pos: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .withClassification(ErrorClassification.CLIENT_ERROR)
      .atPosition(pos.line, pos.column, pos.offset)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N75)
        .withClassification(ErrorClassification.CLIENT_ERROR)
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
