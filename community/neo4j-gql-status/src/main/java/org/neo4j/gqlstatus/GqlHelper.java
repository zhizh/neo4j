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
                .withParam(GqlParams.StringParam.var, variable)
                .withParam(GqlParams.ListParam.valueTypeList, validTypes)
                .build();
    }
}
