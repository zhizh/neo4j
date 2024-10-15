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
package org.neo4j.gqlstatus;

import java.util.List;

public class GqlHelper {

    public static ErrorGqlStatusObject getGql08N06(ErrorGqlStatusObject cause) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N06)
                .withCause(cause)
                .build();
    }

    public static ErrorGqlStatusObject getGql08N06_22N01(
            String value, List<String> expectedValueTypeList, String actualValueType) {
        return getGql08N06(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N01)
                .withParam(GqlParams.StringParam.value, value)
                .withParam(GqlParams.ListParam.valueTypeList, expectedValueTypeList)
                .withParam(GqlParams.StringParam.valueType, actualValueType)
                .build());
    }

    public static ErrorGqlStatusObject getGql08N06_22N03(
            String component, String valueType, Number lower, Number upper, Number value) {
        return GqlHelper.getGql08N06(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N03)
                .withParam(GqlParams.StringParam.component, component)
                .withParam(GqlParams.StringParam.valueType, valueType)
                .withParam(GqlParams.NumberParam.lower, lower)
                .withParam(GqlParams.NumberParam.upper, upper)
                .withParam(GqlParams.NumberParam.value, value)
                .build());
    }

    public static ErrorGqlStatusObject getGql08N06_22N04(String input, String context, List<String> inputList) {
        return getGql08N06(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N04)
                .withParam(GqlParams.StringParam.input, input)
                .withParam(GqlParams.StringParam.context, context)
                .withParam(GqlParams.ListParam.inputList, inputList)
                .build());
    }

    public static ErrorGqlStatusObject getGql08N11_22N01(String value, List<String> valueTypeList, String valueType) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N11)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N01)
                        .withParam(GqlParams.StringParam.value, value)
                        .withParam(GqlParams.ListParam.valueTypeList, valueTypeList)
                        .withParam(GqlParams.StringParam.valueType, valueType)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22N27(String input, String variable, List<String> validTypes) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N27)
                .withParam(GqlParams.StringParam.input, input)
                .withParam(GqlParams.StringParam.variable, variable)
                .withParam(GqlParams.ListParam.valueTypeList, validTypes)
                .build();
    }

    public static ErrorGqlStatusObject get22N69_52N02(String idxDescrOrName, String proc) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N69)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.idxDescrOrName, idxDescrOrName)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N02)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.proc, proc)
                        .build())
                .build();
    }

    private static ErrorGqlStatusObject getGql22N77(
            String entityType, long entityId, String tokenType, String token, String[] propKeyList) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N77)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.entityType, entityType)
                .withParam(GqlParams.NumberParam.entityId, entityId)
                .withParam(GqlParams.StringParam.tokenType, tokenType)
                .withParam(GqlParams.StringParam.token, token)
                .withParam(GqlParams.ListParam.propKeyList, List.of(propKeyList))
                .build();
    }

    public static ErrorGqlStatusObject getGql22N77_nodes(long nodeId, String token, String[] propKeyList) {
        return getGql22N77("NODE", nodeId, "label", token, propKeyList);
    }

    public static ErrorGqlStatusObject getGql22N77_relationships(
            long relationshipId, String token, String[] propKeyList) {
        return getGql22N77("RELATIONSHIP", relationshipId, "type", token, propKeyList);
    }

    public static ErrorGqlStatusObject getGql22N81(String exprType, String context, int line, int column, int offset) {
        var builder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N81)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.exprType, exprType)
                .withParam(GqlParams.StringParam.context, context);
        if (offset >= 0) {
            builder.atPosition(line, column, offset);
        }
        return builder.build();
    }

    public static ErrorGqlStatusObject getGql22N81(String exprType, String context) {
        return getGql22N81(exprType, context, -1, -1, -1);
    }

    public static ErrorGqlStatusObject getGql42N45(int line, int column, int offset) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N45)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(line, column, offset)
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_22N04(
            String input, String context, List<?> validTypes, int line, int column, int offset) {
        ErrorGqlStatusObjectImplementation.Builder main = ErrorGqlStatusObjectImplementation.from(
                        GqlStatusInfoCodes.STATUS_42001)
                .atPosition(line, column, offset);
        ErrorGqlStatusObjectImplementation.Builder cause = ErrorGqlStatusObjectImplementation.from(
                        GqlStatusInfoCodes.STATUS_22N04)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(line, column, offset)
                .withParam(GqlParams.StringParam.input, input)
                .withParam(GqlParams.StringParam.context, context)
                .withParam(GqlParams.ListParam.inputList, validTypes);
        if (offset >= 0) {
            main.atPosition(line, column, offset);
            cause.atPosition(line, column, offset);
        }
        return main.withCause(cause.build()).build();
    }

    public static ErrorGqlStatusObject getGql42001_22N04(String input, String variable, List<?> validTypes) {
        return getGql42001_22N04(input, variable, validTypes, -1, -1, -1);
    }

    public static ErrorGqlStatusObject getGql42001_42I06(
            String input, List<String> valueList, int line, int column, int offset) {
        return getGql42001_42I06_withCause(input, valueList, line, column, offset, null);
    }

    public static ErrorGqlStatusObject getGql42001_42I06_withCause(
            String input, List<String> valueList, int line, int column, int offset, ErrorGqlStatusObject cause) {
        var gqlBuilder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I06)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(line, column, offset)
                .withParam(GqlParams.StringParam.input, input)
                .withParam(GqlParams.ListParam.valueList, valueList);
        var causeChain = cause != null ? gqlBuilder.withCause(cause).build() : gqlBuilder.build();
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(line, column, offset)
                .withCause(causeChain)
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42I20(
            String input, String labelExpr, int line, int column, int offset) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(line, column, offset)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I20)
                        .withParam(GqlParams.StringParam.input, input)
                        .withParam(GqlParams.StringParam.labelExpr, labelExpr)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .atPosition(line, column, offset)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42I29(
            String input, String replacement, int line, int column, int offset) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(line, column, offset)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I29)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .atPosition(line, column, offset)
                        .withParam(GqlParams.StringParam.input, input)
                        .withParam(GqlParams.StringParam.replacement, replacement)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N45(int line, int column, int offset) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(line, column, offset)
                .withCause(getGql42N45(line, column, offset))
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42I47(String msg, int line, int column, int offset) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(line, column, offset)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I47)
                        .withParam(GqlParams.StringParam.msg, msg)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .atPosition(line, column, offset)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject get51N00_50N00(String msgTitle, String msg) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N00)
                        .withParam(GqlParams.StringParam.msgTitle, msgTitle)
                        .withParam(GqlParams.StringParam.msg, msg)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject get51N00_52N35(String procClass, String msg) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N35)
                        .withParam(GqlParams.StringParam.procClass, procClass)
                        .withParam(GqlParams.StringParam.msg, msg)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject get51N00() {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .build();
    }

    public static ErrorGqlStatusObject getGql52N02_52N11(String procedure) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N02)
                .withParam(GqlParams.StringParam.proc, procedure)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N11)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject get52N33(String sig, String msg) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N33)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.sig, sig)
                .withParam(GqlParams.StringParam.msg, msg)
                .build();
    }

    public static ErrorGqlStatusObject get52N34(String sig) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N34)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.sig, sig)
                .build();
    }

    /**
     * Append the exception cause as the bottom GQL cause of the inner ErrorGqlStatusObject if the following applies
     * - the exception cause is an ErrorGqlStatusObject (and not e.g. a generic Java exception)
     * - the inner ErrorGqlStatusObject is of type ErrorGqlStatusObjectImplementation
     * (this should always be true, but is needed for casting)
     *
     * @param gqlStatusObject The current inner ErrorGqlStatusObject
     * @param cause The exception cause
     * @return The replaced inner ErrorGqlStatusObject
     */
    public static ErrorGqlStatusObject getInnerGqlStatusObject(ErrorGqlStatusObject gqlStatusObject, Throwable cause) {
        if (cause instanceof ErrorGqlStatusObject gqlStatusObjectCause) {
            return getErrorObjectWithRewrittenCause(gqlStatusObject, gqlStatusObjectCause);
        } else {
            return gqlStatusObject;
        }
    }

    private static ErrorGqlStatusObject getErrorObjectWithRewrittenCause(
            ErrorGqlStatusObject gqlStatusObject, ErrorGqlStatusObject exceptionCause) {
        // This should always be true, but needed for casting
        if (gqlStatusObject.gqlStatusObject() instanceof ErrorGqlStatusObjectImplementation gsoImplementation) {
            ErrorGqlStatusObject newCause;
            if (gqlStatusObject.cause().isPresent()) {
                newCause =
                        getErrorObjectWithRewrittenCause(gqlStatusObject.cause().get(), exceptionCause);
            } else {
                newCause = exceptionCause;
            }
            return gsoImplementation.copyWithCause(newCause);
        }
        return gqlStatusObject;
    }
}
