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
package org.neo4j.internal.kernel.api.helpers.traversal.ppbfs;

import java.util.BitSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.neo4j.memory.HeapEstimator;
import org.neo4j.memory.Measurable;

public interface Lengths extends Measurable {
    int NONE = -1;

    enum Type {
        Source(0),
        ConfirmedSource(1);

        private final int offset;

        Type(int offset) {
            this.offset = offset;
        }
    }

    boolean get(int index, RelTrackingLengths.Type type);

    void set(int index, RelTrackingLengths.Type type);

    void clear(int index, RelTrackingLengths.Type type);

    int max(RelTrackingLengths.Type type);

    int next(int start, RelTrackingLengths.Type type);

    int min(RelTrackingLengths.Type type);

    boolean isEmpty(RelTrackingLengths.Type type);

    boolean isEmpty();

    String renderSourceLengths();

    static Lengths relationshipUniquenessTrackingLengths() {
        return new RelTrackingLengths();
    }

    static Lengths nonRelationshipUniquenessTrackingLengths() {
        return new NonTrackingLengths();
    }

    /**
     * Represents the presence or absence of different types of length:
     * - Length from source
     * - Confirmed length from source (no duplicate relationships)
     * <p>
     * Implemented by interleaving multiple indexes into a single bitset, in order to conserve memory (we create many of these).
     */
    class RelTrackingLengths extends BitSet implements Lengths {
        private static final int FACTOR = Type.values().length;

        private static final long SHALLOW_SIZE =
                HeapEstimator.shallowSizeOfInstance(RelTrackingLengths.class) + HeapEstimator.sizeOfLongArray(1);

        @Override
        public boolean get(int index, Type type) {
            return get(index * FACTOR + type.offset);
        }

        @Override
        public void set(int index, Type type) {
            set(index * FACTOR + type.offset);
        }

        @Override
        public void clear(int index, Type type) {
            clear(index * FACTOR + type.offset);
        }

        @Override
        public int max(Type type) {
            return stream(type).max().orElse(NONE);
        }

        @Override
        public int next(int start, Type type) {
            for (int i = nextSetBit(start * FACTOR + type.offset); i != -1; i = nextSetBit(i + 1)) {
                if (i % FACTOR == type.offset) {
                    return i / FACTOR;
                }
            }
            return NONE;
        }

        @Override
        public int min(Type type) {
            return next(0, type);
        }

        @Override
        public boolean isEmpty(Type type) {
            if (isEmpty()) {
                return true;
            }

            return min(type) == NONE;
        }

        @Override
        public String renderSourceLengths() {
            return stream(Lengths.Type.Source)
                    .mapToObj(i -> i + (get(i, Lengths.Type.ConfirmedSource) ? "✓" : "?"))
                    .collect(Collectors.joining(",", "{", "}"));
        }

        @Override
        public long estimatedHeapUsage() {
            return SHALLOW_SIZE;
        }

        private IntStream stream(Type type) {
            return stream().filter(i -> i % FACTOR == type.offset).map(i -> i / FACTOR);
        }
    }

    class NonTrackingLengths extends BitSet implements Lengths {

        private static final long SHALLOW_SIZE = HeapEstimator.shallowSizeOfInstance(NonTrackingLengths.class);

        @Override
        public boolean get(int index, Type type) {
            return get(index);
        }

        @Override
        public void set(int index, Type type) {
            if (type == Type.ConfirmedSource) {
                return;
            }
            set(index);
        }

        @Override
        public void clear(int index, Type type) {
            if (type == Type.ConfirmedSource) {
                assert false : "Should not clear ConfirmedSource for NonTrackingLengths";
                return;
            }
            clear(index);
        }

        @Override
        public int max(Type type) {
            return stream().max().orElse(NONE);
        }

        @Override
        public int next(int start, Type type) {
            return nextSetBit(start);
        }

        @Override
        public int min(Type type) {
            return next(0, type);
        }

        @Override
        public boolean isEmpty(Type type) {
            return isEmpty();
        }

        @Override
        public String renderSourceLengths() {
            return toString();
        }

        @Override
        public long estimatedHeapUsage() {
            return SHALLOW_SIZE;
        }
    }
}
