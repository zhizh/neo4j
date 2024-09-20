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
package org.neo4j.procedure.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.neo4j.function.Predicates;
import org.neo4j.internal.kernel.api.procs.QualifiedName;
import org.neo4j.kernel.api.QueryLanguageScope;

class ProcedureHolderTest {
    @Test
    void shouldGetProcedureFromHolder() {
        // given
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        QualifiedName qualifiedName = new QualifiedName("CaseSensitive");
        String item = "CaseSensitiveItem";
        procHolder.put(qualifiedName, QueryLanguageScope.ALL_SCOPES, item, false);

        // then
        assertThat(procHolder.getByKey(qualifiedName, QueryLanguageScope.CYPHER_5))
                .isEqualTo(item);
        assertThat(procHolder.idOfKey(qualifiedName, QueryLanguageScope.CYPHER_5))
                .isEqualTo(0);
    }

    @Test
    void okToHaveProcsOnlyDifferByCase() {
        // given
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        procHolder.put(new QualifiedName("CASESENSITIVE"), QueryLanguageScope.ALL_SCOPES, "CASESENSITIVEItem", false);
        procHolder.put(new QualifiedName("CaseSensitive"), QueryLanguageScope.ALL_SCOPES, "CaseSensitiveItem", false);

        // then
        assertThat(procHolder.getByKey(new QualifiedName("CASESENSITIVE"), QueryLanguageScope.CYPHER_5))
                .isEqualTo("CASESENSITIVEItem");
        assertThat(procHolder.getByKey(new QualifiedName("CaseSensitive"), QueryLanguageScope.CYPHER_5))
                .isEqualTo("CaseSensitiveItem");
        assertThat(procHolder.idOfKey(new QualifiedName("CASESENSITIVE"), QueryLanguageScope.CYPHER_5))
                .isEqualTo(0);
        assertThat(procHolder.idOfKey(new QualifiedName("CaseSensitive"), QueryLanguageScope.CYPHER_5))
                .isEqualTo(1);
    }

    @Test
    void shouldGetCaseInsensitiveFromHolder() {
        // given
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        QualifiedName qualifiedName = new QualifiedName("CaseInSensitive");
        String item = "CaseInSensitiveItem";
        procHolder.put(qualifiedName, QueryLanguageScope.ALL_SCOPES, item, true);

        // then
        QualifiedName lowerCaseName = new QualifiedName("caseinsensitive");
        assertThat(procHolder.getByKey(lowerCaseName, QueryLanguageScope.CYPHER_5))
                .isEqualTo(item);
        assertThat(procHolder.idOfKey(lowerCaseName, QueryLanguageScope.CYPHER_5))
                .isEqualTo(0);
    }

    @Test
    void canOverwriteFunctionAndChangeCaseSensitivity() {
        // given
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        QualifiedName qualifiedName = new QualifiedName("CaseInSensitive");
        String item = "CaseInSensitiveItem";
        procHolder.put(qualifiedName, QueryLanguageScope.ALL_SCOPES, item, true);

        // then
        QualifiedName lowerCaseName = new QualifiedName("caseinsensitive");
        assertThat(procHolder.getByKey(lowerCaseName, QueryLanguageScope.CYPHER_5))
                .isEqualTo(item);
        assertThat(procHolder.idOfKey(lowerCaseName, QueryLanguageScope.CYPHER_5))
                .isEqualTo(0);

        // and then
        procHolder.put(qualifiedName, QueryLanguageScope.ALL_SCOPES, item, false);
        assertNull(procHolder.getByKey(lowerCaseName, QueryLanguageScope.CYPHER_5));
        assertThrows(
                NoSuchElementException.class, () -> procHolder.idOfKey(lowerCaseName, QueryLanguageScope.CYPHER_5));
    }

    @Test
    void preservesIdsForUnregisteredItems() {
        // given
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        QualifiedName qn = new QualifiedName("CaseInSensitive");
        int id = procHolder.put(qn, QueryLanguageScope.ALL_SCOPES, "value", true);

        // when
        procHolder.unregister(qn);
        procHolder.put(qn, QueryLanguageScope.ALL_SCOPES, "value", true);

        // then
        assertThat(procHolder.idOfKey(qn, QueryLanguageScope.CYPHER_5)).isEqualTo(id);
    }

    @Test
    void tombstoneProcedureHolderPreservesRequested() {
        // given
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        QualifiedName qn = new QualifiedName("CaseInSensitive");
        String item = "CaseInSensitiveItem";

        int id = procHolder.put(qn, QueryLanguageScope.ALL_SCOPES, item, true);

        // when
        var renewed = ProcedureHolder.tombstone(procHolder, Predicates.alwaysFalse());

        // then
        assertThat(renewed.getById(id)).isEqualTo(item);
        for (var scope : QueryLanguageScope.values()) {
            assertThat(renewed.getByKey(qn, scope)).isEqualTo(item);
            assertThat(renewed.idOfKey(qn, scope)).isEqualTo(id);
        }
    }

    @Test
    void tombstoneProcedureHolderRemovesOther() {
        // given
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        QualifiedName qn = new QualifiedName("CaseInSensitive");
        String item = "CaseInSensitiveItem";
        int id = procHolder.put(qn, QueryLanguageScope.ALL_SCOPES, item, true);

        // when
        var renewed = ProcedureHolder.tombstone(procHolder, Predicates.alwaysTrue());

        // then
        assertNull(renewed.getById(id));
        for (var scope : QueryLanguageScope.values()) {
            assertNull(renewed.getByKey(qn, scope));
            assertThatThrownBy(() -> renewed.idOfKey(qn, scope)).isInstanceOf(NoSuchElementException.class);
        }
    }

    @Test
    void tombstoneProcedureHolderPreservesIdsAndNamesForRestoredEntries() {
        // given
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        QualifiedName qn = new QualifiedName("CaseInSensitive");
        QualifiedName qn2 = new QualifiedName("qn2");
        String item = "CaseInSensitiveItem";
        int removedId = procHolder.put(qn, QueryLanguageScope.ALL_SCOPES, item, true),
                keptId = procHolder.put(qn2, QueryLanguageScope.ALL_SCOPES, item, true);

        // when
        var renewed = ProcedureHolder.tombstone(procHolder, (qual) -> qual.equals(qn2));
        renewed.put(qn, QueryLanguageScope.ALL_SCOPES, item, true);
        renewed.put(qn2, QueryLanguageScope.ALL_SCOPES, item, true);

        // then
        assertThat(renewed.getByKey(qn, QueryLanguageScope.CYPHER_5)).isEqualTo(item);
        assertThat(renewed.idOfKey(qn, QueryLanguageScope.CYPHER_5)).isEqualTo(removedId);
        assertThat(renewed.getByKey(qn2, QueryLanguageScope.CYPHER_5)).isEqualTo(item);
        assertThat(renewed.idOfKey(qn2, QueryLanguageScope.CYPHER_5)).isEqualTo(keptId);
    }

    @Test
    void canAddSeparateScopes() {
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        QualifiedName qn = new QualifiedName("qn");
        procHolder.put(qn, Set.of(QueryLanguageScope.CYPHER_5), "left", false);
        procHolder.put(qn, Set.of(QueryLanguageScope.CYPHER_25), "right", false);
        assertThat(procHolder.getByKey(qn, QueryLanguageScope.CYPHER_5)).isEqualTo("left");
        assertThat(procHolder.getByKey(qn, QueryLanguageScope.CYPHER_25)).isEqualTo("right");
    }

    @Test
    void canAddJointScope() {
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        QualifiedName qn = new QualifiedName("qn");
        procHolder.put(qn, QueryLanguageScope.ALL_SCOPES, "both", false);
        assertThat(procHolder.getByKey(qn, QueryLanguageScope.CYPHER_5)).isEqualTo("both");
        assertThat(procHolder.getByKey(qn, QueryLanguageScope.CYPHER_25)).isEqualTo("both");
    }

    @Test
    void canUpdateToJointScope() {
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        QualifiedName qn = new QualifiedName("qn");
        procHolder.put(qn, Set.of(QueryLanguageScope.CYPHER_5), "left", false);
        procHolder.put(qn, Set.of(QueryLanguageScope.CYPHER_25), "right", false);
        procHolder.put(qn, QueryLanguageScope.ALL_SCOPES, "both", false);
        assertThat(procHolder.getByKey(qn, QueryLanguageScope.CYPHER_5)).isEqualTo("both");
        assertThat(procHolder.getByKey(qn, QueryLanguageScope.CYPHER_25)).isEqualTo("both");
    }

    @Test
    void canUpdateToSeparateScopes() {
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        QualifiedName qn = new QualifiedName("qn");
        procHolder.put(qn, QueryLanguageScope.ALL_SCOPES, "both", false);
        procHolder.put(qn, Set.of(QueryLanguageScope.CYPHER_5), "left", false);
        procHolder.put(qn, Set.of(QueryLanguageScope.CYPHER_25), "right", false);
        assertThat(procHolder.getByKey(qn, QueryLanguageScope.CYPHER_5)).isEqualTo("left");
        assertThat(procHolder.getByKey(qn, QueryLanguageScope.CYPHER_25)).isEqualTo("right");
    }

    @Test
    void shouldBeAbleToQueryPerScope() {
        ProcedureHolder<String> procHolder = new ProcedureHolder<>();
        String value = "Hello";
        QualifiedName qn = new QualifiedName("qn");
        procHolder.put(qn, QueryLanguageScope.ALL_SCOPES, value, false);
        assertThat(procHolder.getByKey(qn, QueryLanguageScope.CYPHER_5)).isEqualTo(value);
        assertThat(procHolder.getByKey(qn, QueryLanguageScope.CYPHER_25)).isEqualTo(value);
    }
}
