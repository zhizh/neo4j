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
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

public class CypherExecutionException extends Neo4jException {

    @Deprecated
    public CypherExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public CypherExecutionException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, message, cause);
    }

    @Deprecated
    public CypherExecutionException(String message) {
        super(message);
    }

    public CypherExecutionException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    public static CypherExecutionException csvBufferSizeOverflow(Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N49)
                        .build())
                .build();
        return new CypherExecutionException(
                gql,
                """
                Tried to read a field larger than the current buffer size.
                 Make sure that the field doesn't have an unterminated quote,
                 if it doesn't you can try increasing the buffer size via `dbms.import.csv.buffer_size`.""",
                cause);
    }

    public static CypherExecutionException internalError(String msgTitle, String msg, Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N00)
                .withParam(GqlParams.StringParam.msgTitle, msgTitle)
                .withParam(GqlParams.StringParam.msg, msg)
                .build();
        return new CypherExecutionException(gql, msg, cause);
    }

    public static CypherExecutionException unexpectedError(Throwable cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N00)
                .withParam(GqlParams.StringParam.msgTitle, "Unexpected error")
                .withParam(GqlParams.StringParam.msg, cause.getMessage())
                .build();
        return new CypherExecutionException(gql, cause.getMessage(), cause);
    }

    public static CypherExecutionException unrecognisedExecutionMode(String procedure, String mode) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N02)
                .withClassification(ErrorClassification.DATABASE_ERROR)
                .withParam(GqlParams.StringParam.proc, procedure)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N03)
                        .withClassification(ErrorClassification.DATABASE_ERROR)
                        .withParam(GqlParams.StringParam.proc, procedure)
                        .withParam(GqlParams.StringParam.procExeMode, mode)
                        .build())
                .build();
        return new CypherExecutionException(
                gql, "Unable to execute procedure, because it requires an unrecognized execution mode: " + mode, null);
    }

    @Override
    public Status status() {
        Throwable cause = getCause();
        if (cause instanceof Status.HasStatus) {
            return ((Status.HasStatus) cause).status();
        } else {
            return Status.Statement.ExecutionFailed;
        }
    }
}
