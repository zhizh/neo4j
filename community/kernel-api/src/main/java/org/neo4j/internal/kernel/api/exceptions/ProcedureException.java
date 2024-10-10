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
package org.neo4j.internal.kernel.api.exceptions;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

import java.util.List;
import org.neo4j.exceptions.KernelException;
import org.neo4j.gqlstatus.ErrorClassification;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.internal.kernel.api.procs.DescribedSignature;
import org.neo4j.internal.kernel.api.procs.ProcedureSignature;
import org.neo4j.internal.kernel.api.procs.QualifiedName;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserAggregationResult;
import org.neo4j.procedure.UserAggregationUpdate;

public class ProcedureException extends KernelException {
    @Deprecated
    public ProcedureException(Status statusCode, Throwable cause, String message, Object... parameters) {
        super(statusCode, cause, message, parameters);
    }

    public ProcedureException(
            ErrorGqlStatusObject gqlStatusObject,
            Status statusCode,
            Throwable cause,
            String message,
            Object... parameters) {
        super(gqlStatusObject, statusCode, cause, message, parameters);
    }

    @Deprecated
    public ProcedureException(Status statusCode, String message, Object... parameters) {
        super(statusCode, message, parameters);
    }

    public ProcedureException(
            ErrorGqlStatusObject gqlStatusObject, Status statusCode, String message, Object... parameters) {
        super(gqlStatusObject, statusCode, message, parameters);
    }

    public static ProcedureException noSuchProcedure(QualifiedName name) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N08)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procFun, name.toString())
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureNotFound,
                "There is no procedure with the name `%s` registered for this database instance. "
                        + "Please ensure you've spelled the procedure name correctly and that the "
                        + "procedure is properly deployed.",
                name);
    }

    public static ProcedureException noSuchProcedure(int id) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N08)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procFun, Integer.toString(id))
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureNotFound,
                "There is no procedure with the internal id `%d` registered for this database instance.",
                id);
    }

    public static ProcedureException noSuchFunction(int id) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N08)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procFun, Integer.toString(id))
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureNotFound,
                "There is no function with the internal id `%d` registered for this database instance.",
                id);
    }

    public static ProcedureException noSuchProcedureOrFunction(String name) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N08)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.procFun, name)
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureCallFailed,
                "There is no `%s` in the current procedure call context.",
                name);
    }

    public static ProcedureException noSuchConstituentGraph(String graphName, String ctxDatabaseName) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42002)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N01)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.graph, graphName)
                        .withParam(GqlParams.StringParam.db, ctxDatabaseName)
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureCallFailed,
                "'%s' is not a constituent of composite database '%s'".formatted(graphName, ctxDatabaseName));
    }

    public static ProcedureException faultyClassFieldAnnotationStatic(String procField, String procClass) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N01)
                        .withParam(GqlParams.StringParam.procField, procField)
                        .withParam(GqlParams.StringParam.procClass, procClass)
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "The field `%s` in the class named `%s` is annotated as a @Context field,%n"
                        + "but it is static. @Context fields must be public, non-final and non-static,%n"
                        + "because they are reset each time a procedure is invoked.",
                procField,
                procClass);
    }

    public static ProcedureException unableToAccessFieldInjection(String procClass, String procField) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N03)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procClass, procClass)
                        .withParam(GqlParams.StringParam.procField, procField)
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "Unable to set up injection for `%s`, failed to access field `%s",
                procClass,
                procField);
    }

    public static ProcedureException unableToAccessField(String procClass, String procField) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N03)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procClass, procClass)
                        .withParam(GqlParams.StringParam.procField, procField)
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.TypeError,
                "Field `%s` in record `%s` cannot be accessed. Please ensure the field is marked as `public`.",
                procField,
                procClass);
    }

    public static ProcedureException missingClassFieldAnnotation(String procClass, String procField) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N04)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procClass, procClass)
                        .withParam(GqlParams.StringParam.procField, procField)
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "Field `%s` on `%s` is not annotated as a @" + Context.class.getSimpleName()
                        + " and is not static. If you want to store state along with your procedure,"
                        + " please use a static field.",
                procField,
                procClass);
    }

    public static ProcedureException faultyClassFieldAnnotation(String procClass, String procField) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N05)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procClass, procClass)
                        .withParam(GqlParams.StringParam.procField, procField)
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "Field `%s` on `%s` must be non-final and public.",
                procField,
                procClass);
    }

    public static ProcedureException missingArgumentAnnotation(int position, String procMethod) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N06)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.NumberParam.pos, position)
                        .withParam(GqlParams.StringParam.procMethod, procMethod)
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "Argument at position %d in method `%s` is missing an `@%s` annotation.%n"
                        + "Please add the annotation, recompile the class and try again.",
                position,
                procMethod,
                Name.class.getSimpleName());
    }

    public static ProcedureException missingArgumentName(int position, String procMethod) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N06)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.NumberParam.pos, position)
                        .withParam(GqlParams.StringParam.procMethod, procMethod)
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "Argument at position %d in method `%s` is annotated with a name,%n"
                        + "but the name is empty, please provide a non-empty name for the argument.",
                position,
                procMethod,
                Name.class.getSimpleName());
    }

    public static ProcedureException invalidOrderingOfDefaultArguments(
            int position, String parameterValue, String procMethod) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N07)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procFun, "procedure")
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "Non-default argument at position %d with name %s in method %s follows default argument. "
                        + "Add a default value or rearrange arguments so that the non-default values comes first.",
                position,
                parameterValue,
                procMethod);
    }

    public static ProcedureException duplicatedAnnotatedMethods(String procClass, String className) {

        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N08)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procClass, procClass)
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "Class '%s' contains multiple methods annotated with '@%s'.",
                procClass,
                className);
    }

    public static ProcedureException missingAnnotatedMethods(String procClass) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N08)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procClass, procClass)
                        .build())
                .build();

        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "Class '%s' must contain methods annotated with both '@%s' as well as '@%s'.",
                procClass,
                UserAggregationResult.class.getSimpleName(),
                UserAggregationUpdate.class.getSimpleName());
    }

    public static ProcedureException methodMustBeVoid(String procClass, String procMethod, String returnType) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N09)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procClass, procClass)
                        .withParam(GqlParams.StringParam.procMethod, procMethod)
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "Update method '%s' in %s has type '%s' but must have return type 'void'.",
                procMethod,
                procClass,
                returnType);
    }

    public static ProcedureException aggregationMethodNotPublic(String procClass, String procMethod) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N09)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procClass, procClass)
                        .withParam(GqlParams.StringParam.procMethod, procMethod)
                        .build())
                .build();
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "Aggregation update method '%s' in %s must be public.",
                procMethod,
                procClass);
    }

    public static ProcedureException nonReloadableNamespaces(List<String> nonReloadableNamespaces, Status statusCode) {
        ErrorGqlStatusObject gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N16)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N23)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.ListParam.namespaceList, nonReloadableNamespaces)
                        .build())
                .build();
        return new ProcedureException(
                gql, statusCode, "The following namespaces are not reloadable: %s.".formatted(nonReloadableNamespaces));
    }

    public static ProcedureException loadFailedSandboxed(DescribedSignature signature) {
        var gql = GqlHelper.get52N34(String.valueOf(signature.name()));
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                signature.description().orElse("Failed to load " + signature.name()));
    }

    public static ProcedureException noSuchIndex(String indexName, String procedureName, Boolean formatIndex) {
        var gql = GqlHelper.get22N69_52N02(indexName, "db." + procedureName);
        if (formatIndex) {
            indexName = "'" + indexName + "'";
        }
        return new ProcedureException(gql, Status.Schema.IndexNotFound, "No such index %s", indexName);
    }

    public static ProcedureException surpressedRegisterFailed(List<Throwable> surpressedExceptions) {
        var exception = surpressedExceptions.get(surpressedExceptions.size() - 1);
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N00)
                .withClassification(ErrorClassification.UNKNOWN)
                .withParam(GqlParams.StringParam.msgTitle, exception.getClass().getName())
                .withParam(GqlParams.StringParam.msg, exception.getMessage())
                .build();
        for (int i = surpressedExceptions.size() - 2; i >= 0; i--) {
            exception = surpressedExceptions.get(i);
            gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N00)
                    .withClassification(ErrorClassification.UNKNOWN)
                    .withParam(
                            GqlParams.StringParam.msgTitle, exception.getClass().getName())
                    .withParam(GqlParams.StringParam.msg, exception.getMessage())
                    .withCause(gql)
                    .build();
        }
        gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(gql)
                .build();

        var exc = new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                "Failed to register procedures for the following reasons:");
        for (var surpressedException : surpressedExceptions) {
            exc.addSuppressed(surpressedException);
        }
        return exc;
    }

    public static ProcedureException innerExceptionFailed(Throwable throwable, ProcedureSignature signature) {
        Throwable cause = getRootCause(throwable); // Do we risk losing valuable information here
        var gql = GqlHelper.get52N33(
                String.valueOf(signature.name()), (cause != null ? String.valueOf(cause) : String.valueOf(throwable)));

        if (throwable instanceof Status.HasStatus statusException) {
            return new ProcedureException(gql, statusException.status(), throwable, throwable.getMessage());
        } else {
            return new ProcedureException(
                    gql,
                    Status.Procedure.ProcedureCallFailed,
                    throwable,
                    "Failed to invoke procedure `%s`: %s",
                    signature.name(),
                    "Caused by: " + (cause != null ? cause : throwable));
        }
    }

    public static ProcedureException compilationFailed(String routine, String procedureName, Throwable cause) {
        var gql = GqlHelper.get51N00_52N35(procedureName, cause.getMessage());
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureRegistrationFailed,
                cause,
                "Failed to compile %s defined in `%s`: %s",
                routine,
                procedureName,
                cause.getMessage());
    }

    public static ProcedureException invocationFailed(String typeAndName, Throwable cause) {
        Throwable rootCause = getRootCause(cause); // Do we risk losing valuable information here
        var gql = GqlHelper.get52N33(
                typeAndName, (rootCause != null ? String.valueOf(rootCause) : String.valueOf(cause)));
        return new ProcedureException(
                gql,
                Status.Procedure.ProcedureCallFailed,
                cause,
                "Failed to invoke %s: %s",
                typeAndName,
                "Caused by: " + (rootCause != null ? rootCause : cause));
    }
}
