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
package org.neo4j.exceptions;

import org.neo4j.gqlstatus.ErrorClassification;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

public class InvalidSemanticsException extends Neo4jException {
    @Deprecated
    public InvalidSemanticsException(String message, Throwable cause) {
        super(message, cause);
    }

    private InvalidSemanticsException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, message, cause);
    }

    @Deprecated
    public InvalidSemanticsException(String message) {
        super(message);
    }

    private InvalidSemanticsException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    public static InvalidSemanticsException invalidCombinationOfProfileAndExplain() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N52)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .build())
                .build();
        return new InvalidSemanticsException(gql, "Can't mix PROFILE and EXPLAIN");
    }

    @Override
    public Status status() {
        return Status.Statement.SemanticError;
    }
}
