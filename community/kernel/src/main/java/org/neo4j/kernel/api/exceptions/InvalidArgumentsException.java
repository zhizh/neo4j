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
package org.neo4j.kernel.api.exceptions;

import org.neo4j.gqlstatus.ErrorClassification;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlException;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;

public class InvalidArgumentsException extends GqlException implements Status.HasStatus {
    private final Status status;

    @Deprecated
    public InvalidArgumentsException(String message) {
        this(message, null);
    }

    public InvalidArgumentsException(ErrorGqlStatusObject gqlStatusObject, String message) {
        this(gqlStatusObject, message, null);
    }

    @Deprecated
    public InvalidArgumentsException(String message, Throwable cause) {
        super(message, cause);
        this.status = Status.General.InvalidArguments;
    }

    public InvalidArgumentsException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, message, cause);
        this.status = Status.General.InvalidArguments;
    }

    public static InvalidArgumentsException requiresPositiveInteger(String option, int value) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22003)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N02)
                        .withParam(GqlParams.StringParam.option, option)
                        .withParam(GqlParams.NumberParam.value, value)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .build())
                .build();
        return new InvalidArgumentsException(
                gql, String.format("Option `%s` requires positive integer argument, got `%d`", option, value));
    }

    @Override
    public Status status() {
        return status;
    }
}
