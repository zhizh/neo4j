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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GqlRuntimeExceptionTest {
    @Test
    void shouldNotConvertJavaExceptionToCause() {
        ErrorGqlStatusObject gqlObject = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N11)
                .build();
        Throwable cause = new RuntimeException();
        ErrorGqlStatusObject errorWithJavaExceptionCause = new GqlRuntimeException(gqlObject, "message", cause) {};
        assertEquals(Optional.empty(), errorWithJavaExceptionCause.cause());
    }

    @Test
    void shouldConvertGqlExceptionToCause() {
        ErrorGqlStatusObject gqlObject = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N11)
                .build();
        ErrorGqlStatusObject causeGqlObject = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N08)
                .withParam(GqlParams.StringParam.option1, "myOption")
                .withParam(GqlParams.StringParam.option2, "yourOption")
                .build();
        Throwable cause = new GqlRuntimeException(causeGqlObject, "inner message") {};
        ErrorGqlStatusObject errorWithGqlExceptionCause = new GqlRuntimeException(gqlObject, "message", cause) {};
        assertTrue(errorWithGqlExceptionCause.cause().isPresent());
        assertEquals(
                GqlStatusInfoCodes.STATUS_22N08.getStatusString(),
                errorWithGqlExceptionCause.cause().get().gqlStatus());
        assertThat(errorWithGqlExceptionCause.cause().get().statusDescription())
                .contains("cannot combine 'myOption' with 'yourOption'");
    }

    @Test
    void shouldAppendGqlExceptionToExistingGqlCause() {
        ErrorGqlStatusObject existingCauseGqlObject = ErrorGqlStatusObjectImplementation.from(
                        GqlStatusInfoCodes.STATUS_22N06)
                .withParam(GqlParams.StringParam.option, "option")
                .build();

        ErrorGqlStatusObject gqlObject = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_25N11)
                .withCause(existingCauseGqlObject)
                .build();

        ErrorGqlStatusObject exceptionCauseGqlObject = ErrorGqlStatusObjectImplementation.from(
                        GqlStatusInfoCodes.STATUS_22N08)
                .withParam(GqlParams.StringParam.option1, "myOption")
                .withParam(GqlParams.StringParam.option2, "yourOption")
                .build();
        Throwable cause = new GqlRuntimeException(exceptionCauseGqlObject, "inner message") {};
        ErrorGqlStatusObject errorWithGqlExceptionCause = new GqlRuntimeException(gqlObject, "message", cause) {};

        assertTrue(errorWithGqlExceptionCause.cause().isPresent());
        ErrorGqlStatusObject firstCause = errorWithGqlExceptionCause.cause().get();
        assertEquals(GqlStatusInfoCodes.STATUS_22N06.getStatusString(), firstCause.gqlStatus());
        assertThat(firstCause.statusDescription()).contains("'option' needs to be specified");

        assertTrue(firstCause.cause().isPresent());
        ErrorGqlStatusObject secondCause = firstCause.cause().get();
        assertEquals(GqlStatusInfoCodes.STATUS_22N08.getStatusString(), secondCause.gqlStatus());
        assertThat(secondCause.statusDescription()).contains("cannot combine 'myOption' with 'yourOption'");
    }
}
