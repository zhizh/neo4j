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
package org.neo4j.dbms.systemgraph.allocation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.util.VisibleForTesting;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.MapValueBuilder;

public final class DatabaseAllocationHints {

    public static DatabaseAllocationHints EMPTY = new DatabaseAllocationHints(Set.of());
    public static Set<String> VALID_HINT_KEYS = Arrays.stream(Hint.class.getPermittedSubclasses())
            .map(DatabaseAllocationHints::key)
            .collect(Collectors.toSet());

    private final Set<Hint<?>> hints;

    private DatabaseAllocationHints(Set<Hint<?>> hints) {
        this.hints = hints;
    }

    public static DatabaseAllocationHints createFromInput(MapValue providedHints) {
        var hints = new HashSet<Hint<?>>();
        providedHints.foreach((k, v) -> hints.add(createHintFromInput(k, v)));

        if (hints.isEmpty()) {
            return EMPTY;
        }
        return new DatabaseAllocationHints(Set.copyOf(hints));
    }

    public static DatabaseAllocationHints createFromGraph(Node allocationHints) {
        var labels = Iterables.asSet(allocationHints.getLabels());
        var labelString = labels.stream().map(Label::name).collect(Collectors.joining(", ", "'", "'"));
        if (!labels.contains(TopologyGraphDbmsModel.ALLOCATION_HINTS_LABEL)) {
            throw new IllegalArgumentException(String.format(
                    "Incorrect Node labels for creating a DatabaseAllocationHints object! Required label %s, found %s",
                    TopologyGraphDbmsModel.ALLOCATION_HINTS_LABEL.name(), labelString));
        }

        Set<Hint<?>> hints = allocationHints.getAllProperties().entrySet().stream()
                .filter(entry -> VALID_HINT_KEYS.contains(entry.getKey()))
                .map(entry -> createHintFromObject(entry.getKey(), entry.getValue()))
                .collect(Collectors.toUnmodifiableSet());
        if (hints.isEmpty()) {
            return EMPTY;
        }
        return new DatabaseAllocationHints(hints);
    }

    /**
     * Throws {@link IllegalArgumentException} if the provided key and value do not combine to form a valid {@link Hint}. Otherwise, does nothing.
     *
     * @param key a string key provided within the allocationHints OPTION for CREATE and ALTER DATABASE commands
     * @param value a Cypher value provided within the allocationHints OPTION for CREATE and ALTER DATABASE commands
     */
    public static void validate(String key, AnyValue value) {
        createHintFromInput(key, value);
    }

    @VisibleForTesting
    static Hint<?> createHintFromInput(String key, AnyValue value) {
        switch (key) {
            case DatabaseWeight.KEY:
                if (value instanceof IntegralValue v) {
                    return new DatabaseWeight((int) v.longValue());
                } else {
                    throw new IllegalArgumentException(String.format(
                            "Incorrect value type provided for allocation hint '%s'. Expected an Integer but found a %s.",
                            DatabaseWeight.KEY, value.getTypeName()));
                }
            default:
                var validKeys = VALID_HINT_KEYS.stream().collect(Collectors.joining(", ", "'", "'"));
                throw new IllegalArgumentException(String.format(
                        "The key %s is not a recognised allocation hint key! Valid hint keys are: %s", key, validKeys));
        }
    }

    private static Hint<?> createHintFromObject(String key, Object value) {
        switch (key) {
            case DatabaseWeight.KEY:
                if (value instanceof Integer v) {
                    return new DatabaseWeight(v);
                } else {
                    throw new IllegalArgumentException(String.format(
                            "Incorrect value type provided for allocation hint '%s'. Expected an Integer but found a %s.",
                            DatabaseWeight.KEY, value.getClass().getSimpleName()));
                }
            default:
                var validKeys = VALID_HINT_KEYS.stream().collect(Collectors.joining(", ", "'", "'"));
                throw new IllegalArgumentException(String.format(
                        "The key %s is not a recognised allocation hint key! Valid hint keys are: %s", key, validKeys));
        }
    }

    /**
     * This method primarily exists to populate the {@link DatabaseAllocationHints#VALID_HINT_KEYS} constant, and is package private rather than private to
     * allow a test to enforce that all permissible subclasses of {@link Hint} define a unique string key.
     *
     * @param hintClass the class tag for the given hint
     * @return the string key for the given hint class. If no key is defined, or if the class is not a hint {@link IllegalArgumentException} is thrown
     */
    @VisibleForTesting
    static String key(Class<?> hintClass) {
        if (!Hint.class.isAssignableFrom(hintClass)) {
            throw new IllegalArgumentException(String.format(
                    "Class does not implement %s: %s", Hint.class.getSimpleName(), hintClass.getSimpleName()));
        }

        if (hintClass == DatabaseWeight.class) {
            return DatabaseWeight.KEY;
        } else {
            throw new IllegalArgumentException("Unexpected hint type with unknown key: " + hintClass.getSimpleName());
        }
    }

    public Set<Hint<?>> hints() {
        return hints;
    }

    public MapValue toMapValue() {
        var hintsMapBuilder = new MapValueBuilder(hints.size());
        hints.forEach(h -> hintsMapBuilder.add(h.getKey(), Values.of(h.getValue())));
        return hintsMapBuilder.build();
    }

    @Override
    public String toString() {
        return "DatabaseAllocationHints{" + "hints=" + hints + '}';
    }

    public sealed interface Hint<T> permits DatabaseWeight {
        String getKey();

        T getValue();
    }
}
