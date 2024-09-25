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
import org.neo4j.collection.trackable.HeapTrackingLongObjectHashMap;
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks.PPBFSHooks;
import org.neo4j.memory.MemoryTracker;

// TODO These method names are very Trail specific, clean up and hide further would be nice
//     Maybe rename to something like TraversalMatchModeTracking and then have it look like
//         Lengths createLengths();
//         SignpostTracker createSignpostTracker();
public interface ExpansionTracker {
    void set(TwoWaySignpost signpost, int depth, int dgLengthToTarget);

    // TODO rename to maybe just validate
    boolean validateTrail(SignpostStack stack, int dgLength, PPBFSHooks hooks);

    // TODO 0 means we don't care about duplicates which is a bit weird
    //     clean up
    int distanceToDuplicate(TwoWaySignpost signpost);

    void popSignpostAtDepth(TwoWaySignpost signpost, int depth, int dgLengthToTarget);

    void clear();

    Lengths createLengths();

    static ExpansionTracker createTracker(MemoryTracker memoryTracker) {
        return new RelationshipTracker(memoryTracker);
    }

    ExpansionTracker NO_TRACKING = new ExpansionTracker() {
        @Override
        public void set(TwoWaySignpost signpost, int depth, int dgLengthToTarget) {
            // do nothing
        }

        @Override
        public boolean validateTrail(SignpostStack stack, int dgLength, PPBFSHooks hooks) {
            return true;
        }

        @Override
        public int distanceToDuplicate(TwoWaySignpost signpost) {
            return 0;
        }

        @Override
        public void popSignpostAtDepth(TwoWaySignpost signpost, int depth, int dgLengthToTarget) {
            // do nothing
        }

        @Override
        public void clear() {
            // do nothing
        }

        @Override
        public Lengths createLengths() {
            return Lengths.nonRelationshipUniquenessTrackingLengths();
        }

        @Override
        public boolean requireUniqueRelationships() {
            return false;
        }
    };

    boolean requireUniqueRelationships();

    class RelationshipTracker implements ExpansionTracker {
        private final HeapTrackingLongObjectHashMap<BitSet> relationshipPresenceAtDepth;

        RelationshipTracker(MemoryTracker memoryTracker) {
            this.relationshipPresenceAtDepth = HeapTrackingLongObjectHashMap.createLongObjectHashMap(memoryTracker);
        }

        @Override
        public void set(TwoWaySignpost signpost, int depth, int dgLengthToTarget) {
            if (signpost instanceof TwoWaySignpost.RelSignpost rel) {
                var depths = this.relationshipPresenceAtDepth.get(rel.relId);
                if (depths == null) {
                    depths = new BitSet();
                    this.relationshipPresenceAtDepth.put(rel.relId, depths);
                }
                depths.set(depth);
            } else if (signpost instanceof TwoWaySignpost.MultiRelSignpost multiRel) {
                for (long relId : multiRel.rels) {
                    var depths = this.relationshipPresenceAtDepth.get(relId);
                    if (depths == null) {
                        depths = new BitSet();
                        this.relationshipPresenceAtDepth.put(relId, depths);
                    }
                    // here we take advantage of the fact that multi rel signposts already have relationship uniqueness,
                    // so we can compress them into a single bit of the depth bitset per rel
                    depths.set(depth);
                }
            }
        }

        @Override
        public boolean validateTrail(SignpostStack stack, int dgLength, PPBFSHooks hooks) {
            int sourceLength = 0;
            for (int i = stack.size() - 1; i >= 0; i--) {
                TwoWaySignpost signpost = stack.signpost(i);
                sourceLength += signpost.dataGraphLength();
                if (signpost instanceof TwoWaySignpost.RelSignpost rel) {

                    // idx       2      1      0
                    // 7    (s)-[0]-()-[1]-()-[0]-(t)
                    // 11   (s)-[1]-()-[0]-()-[1]-(t)
                    var bitset = relationshipPresenceAtDepth.get(rel.relId);
                    assert bitset.get(i);
                    if (bitset.length() > i + 1) {
                        hooks.invalidTrail(stack);
                        return false;
                    }
                } else if (signpost instanceof TwoWaySignpost.MultiRelSignpost rels) {
                    for (int j = 0; j < rels.rels.length; j++) {
                        long relId = rels.rels[j];
                        var bitset = relationshipPresenceAtDepth.get(relId);
                        assert bitset.get(i);
                        if (bitset.length() > i + 1) {
                            hooks.invalidTrail(stack);
                            return false;
                        }
                    }
                }

                // TODO: is this a problem?
                //      it was but handled
                if (!signpost.isVerifiedAtLength(sourceLength)) {
                    signpost.setVerified(sourceLength);
                    if (!signpost.forwardNode.validatedAtLength(sourceLength)) {
                        signpost.forwardNode.setValidatedAtLength(sourceLength, dgLength - sourceLength);
                    }
                }
            }
            return true;
        }

        @Override
        public int distanceToDuplicate(TwoWaySignpost signpost) {
            if (signpost instanceof TwoWaySignpost.RelSignpost rel) {
                var stack = relationshipPresenceAtDepth.get(rel.relId);
                if (stack == null) {
                    return 0;
                }
                int last = stack.length();
                if (last == 0) {
                    return 0;
                }

                int next = stack.previousSetBit(last - 2);
                if (next == -1) {
                    return 0;
                }
                return last - 1 - next;
            } else if (signpost instanceof TwoWaySignpost.MultiRelSignpost rels) {
                var min = 0;
                for (var relId : rels.rels) {
                    var stack = relationshipPresenceAtDepth.get(relId);
                    if (stack == null) {
                        continue;
                    }
                    int last = stack.length();
                    if (last == 0) {
                        continue;
                    }

                    int next = stack.previousSetBit(last - 2);
                    if (next == -1) {
                        continue;
                    }

                    int value = last - 1 - next;

                    if (min == 0) {
                        min = value;
                    } else {
                        min = Math.min(min, value);
                    }
                }
                return min;
            }
            return 0;
        }

        @Override
        public void popSignpostAtDepth(TwoWaySignpost signpost, int depth, int dgLengthToTarget) {
            if (signpost instanceof TwoWaySignpost.RelSignpost rel) {
                var depths = relationshipPresenceAtDepth.get(rel.relId);
                depths.clear(depth);
                if (depths.isEmpty()) {
                    relationshipPresenceAtDepth.remove(rel.relId);
                }
            } else if (signpost instanceof TwoWaySignpost.MultiRelSignpost relsSignpost) {
                for (long relId : relsSignpost.rels) {
                    var depths = relationshipPresenceAtDepth.get(relId);
                    depths.clear(depth);
                    if (depths.isEmpty()) {
                        relationshipPresenceAtDepth.remove(relId);
                    }
                }
            }
        }

        @Override
        public void clear() {
            relationshipPresenceAtDepth.clear();
        }

        @Override
        public Lengths createLengths() {
            return Lengths.relationshipUniquenessTrackingLengths();
        }

        @Override
        public boolean requireUniqueRelationships() {
            return true;
        }
    }
}
