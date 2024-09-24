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

    public static ErrorGqlStatusObject getGql22N27(String input, String variable, List<String> validTypes) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N27)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.input, input)
                .withParam(GqlParams.StringParam.variable, variable)
                .withParam(GqlParams.ListParam.valueTypeList, validTypes)
                .build();
    }

    public static ErrorGqlStatusObject getGql52N02_52N11(String procedure) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N02)
                .withParam(GqlParams.StringParam.proc, procedure)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N11)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
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
