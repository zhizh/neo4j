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

import java.util.List;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.messages.MessageUtil;

public class InvalidArgumentException extends Neo4jException {
    @Deprecated
    public InvalidArgumentException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidArgumentException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, message, cause);
    }

    @Deprecated
    public InvalidArgumentException(String message) {
        super(message);
    }

    public InvalidArgumentException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    @Override
    public Status status() {
        return Status.Statement.ArgumentError;
    }

    public static InvalidArgumentException unknownNormalForm(String normalForm) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N49)
                        .withParam(GqlParams.StringParam.input, normalForm)
                        .build())
                .build();
        return new InvalidArgumentException(gql, "Unknown normal form. Valid values are: NFC, NFD, NFKC, NFKD.");
    }

    public static InvalidArgumentException incompleteSpatialValue(
            String crs, String mandatoryKeys, List<String> mandatoryKeysList) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N18)
                        .withParam(GqlParams.StringParam.crs, String.valueOf(crs))
                        .withParam(GqlParams.ListParam.mapKeyList, mandatoryKeysList)
                        .build())
                .build();
        return new InvalidArgumentException(gql, String.format("A %s point must contain %s", crs, mandatoryKeys));
    }

    public static InvalidArgumentException invalidSpatialValueCombination() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N22)
                        .build())
                .build();
        return new InvalidArgumentException(gql, "Cannot specify both CRS and SRID");
    }

    public static InvalidArgumentException timezoneAndOffsetMismatch(
            String zoneName, String offset, List<String> validOffsets, String matcherGroup) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22003)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N04)
                        .withParam(GqlParams.StringParam.input, zoneName)
                        .withParam(GqlParams.StringParam.context, String.valueOf(offset))
                        .withParam(GqlParams.ListParam.inputList, validOffsets)
                        .build())
                .build();
        return new InvalidArgumentException(gql, "Timezone and offset do not match: " + matcherGroup);
    }

    public static InvalidArgumentException temporalSelectionConflict(String fieldName, String component) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22007)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N14)
                        .withParam(GqlParams.StringParam.temporal, fieldName)
                        .withParam(GqlParams.StringParam.component, component)
                        .build())
                .build();
        throw new InvalidArgumentException(
                gql, String.format("%s cannot be selected together with %s.", fieldName, component));
    }

    public static InvalidArgumentException invalidCoordinateNames() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N19)
                        .build())
                .build();
        return new InvalidArgumentException(
                gql, "A point must contain either 'x' and 'y' or 'latitude' and 'longitude'");
    }

    public static InvalidArgumentException pointWithWrongDimensions(int expectedDimension, int actualDimension) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N20)
                        .withParam(GqlParams.NumberParam.dim1, expectedDimension)
                        .withParam(GqlParams.NumberParam.value, actualDimension)
                        .withParam(GqlParams.NumberParam.dim2, actualDimension)
                        .build())
                .build();

        return new InvalidArgumentException(
                gql,
                String.format(
                        "Cannot create point with %dD coordinate reference system and %d coordinates. "
                                + "Please consider using equivalent %dD coordinate reference system",
                        expectedDimension, actualDimension, actualDimension));
    }

    public static InvalidArgumentException bothAllowedAndDeniedDbs() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N85)
                .build();
        return new InvalidArgumentException(gql, "Can't specify both allowed and denied databases");
    }

    public static InvalidArgumentException invalidEncryptionVersion(String version, List<String> allowedVersions) {
        var gql = GqlHelper.getGql42001_22N04(version, "encryption version", allowedVersions);
        var innerException = new IllegalArgumentException("The encryption version specified is not available.");
        return new InvalidArgumentException(gql, innerException.getMessage(), innerException);
    }

    public static InvalidArgumentException invalidURLScheme(String url, List<String> allowedSchemes) {
        var gql = GqlHelper.getGql42001_22N04(url, "URL scheme", allowedSchemes);
        return new InvalidArgumentException(gql, MessageUtil.invalidScheme(url, allowedSchemes));
    }

    public static InvalidArgumentException insecureURLScheme(String url, List<String> allowedSchemes) {
        var gql = GqlHelper.getGql42001_22N04(url, "URL scheme", allowedSchemes);
        return new InvalidArgumentException(gql, MessageUtil.insecureScheme(url, allowedSchemes));
    }

    public static InvalidArgumentException incorrectPasswordFormat() {
        var innerException = new IllegalArgumentException(
                "Incorrect format of encrypted password. Correct format is '<encryption-version>,<hash>,<salt>'.");
        var gql = GqlHelper.getGql42001_22N04("*", "password format", List.of("'<encryption-version>,<hash>,<salt>'"));
        return new InvalidArgumentException(gql, innerException.getMessage(), innerException);
    }
}
