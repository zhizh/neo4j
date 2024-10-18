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
package org.neo4j.dbms.api;

import org.neo4j.annotations.api.PublicApi;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.kernel.api.exceptions.Status;

/**
 * A {@link DatabaseManagementService} tried to perform some operation on a database, but no database with that name currently exists.
 */
@PublicApi
public class DatabaseNotFoundException extends DatabaseManagementException {
    public DatabaseNotFoundException() {
        super();
    }

    public DatabaseNotFoundException(ErrorGqlStatusObject gqlStatusObject) {
        super(gqlStatusObject);
    }

    public DatabaseNotFoundException(String message) {
        super(message);
    }

    public DatabaseNotFoundException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    public DatabaseNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public DatabaseNotFoundException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, message, cause);
    }

    public DatabaseNotFoundException(Throwable cause) {
        super(cause);
    }

    public DatabaseNotFoundException(ErrorGqlStatusObject gqlStatusObject, Throwable cause) {
        super(gqlStatusObject, cause);
    }

    public static DatabaseNotFoundException failedCreateCompositeAlias(String name, String nonPrettyName) {
        var gql = GqlHelper.getGql42002_42N00(name);
        return new DatabaseNotFoundException(
                gql,
                String.format(
                        "Failed to create the specified database alias '%s': "
                                + "Composite database '%s' does not exist.",
                        name, nonPrettyName));
    }

    public static DatabaseNotFoundException failedDeleteComposite(String name) {
        var gql = GqlHelper.getGql42002_42N00(name);
        return new DatabaseNotFoundException(
                gql,
                String.format(
                        "Failed to delete the specified composite database '%s': Database does not exist.", name));
    }

    public static DatabaseNotFoundException failedAction(String action, String name) {
        var gql = GqlHelper.getGql42002_42N00(name);
        return new DatabaseNotFoundException(
                gql, String.format("Failed to %s the specified database '%s': Database does not exist.", action, name));
    }

    public static DatabaseNotFoundException failedActionAlias(String action, String alias, String name) {
        var gql = GqlHelper.getGql42002_42N00(name);
        return new DatabaseNotFoundException(
                gql,
                String.format(
                        "Failed to %s the specified database alias '%s': Database '%s' does not exist.",
                        action, alias, name));
    }

    public static DatabaseNotFoundException noNameOrAlias(String name) {
        var gql = GqlHelper.getGql42002_42N00(name);
        return new DatabaseNotFoundException(
                gql, String.format("Database '%s' does not exist': No database exists with that name or alias.", name));
    }

    @Override
    public Status status() {
        return Status.Database.DatabaseNotFound;
    }
}
