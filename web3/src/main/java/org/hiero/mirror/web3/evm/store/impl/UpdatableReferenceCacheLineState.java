// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.impl;

import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import org.jspecify.annotations.NonNull;

/** Utility class to compute the state of a cache line.
 *
 * Determines, from the two maps held by an UpdatableReferenceCache - original and current - the state of the
 * cache line.  The state is enumerated by ValueState.
 *
 * Not a true Java "utility" class because it can't hold only static methods.  Because it is generic in the type
 * of the cache key and one method uses that type parameter in its signature.
 *
 * @param <K> cache key type
 */
public class UpdatableReferenceCacheLineState<K> {

    private static final Entry invalidStateMarker = new Entry(ValueState.INVALID, null);
    private static final Entry valueNotYetFetchedMarker = new Entry(ValueState.NOT_YET_FETCHED, null);
    private static final Entry valueMissingMarker = new Entry(ValueState.MISSING, null);
    private static final Entry valueDeletedMarker = new Entry(ValueState.DELETED, null);
    /** 2D array to map `(original state x current state)` to full cache line state w.r.t. this cache and upstream caches */
    private static final ValueState[ /*original*/][ /*current*/] toValueState = {
        /*MISSING*/ {ValueState.NOT_YET_FETCHED, ValueState.INVALID, ValueState.UPDATED},
        /*NULL*/ {ValueState.MISSING, ValueState.INVALID, ValueState.UPDATED},
        /*NON_NULL*/ {ValueState.PRESENT, ValueState.DELETED, ValueState.UPDATED}
    };

    private static final BinaryOperator<Object> fromNull = (o, c) -> null;
    private static final BinaryOperator<Object> fromO = (o, d) -> o;
    private static final BinaryOperator<Object> fromC = (o, c) -> c;
    /** 2D array to map `(original value x current value)` to returned value for this cache line */
    private static final List<List<BinaryOperator<Object>>> toValue = List.of(
            /*MISSING*/ List.of(fromNull, fromNull, fromC),
            /*NULL*/ List.of(fromNull, fromNull, fromC),
            /*NON_NULL*/ List.of(fromO, fromNull, fromC));

    /** Get the current state/value of the key in this cache - N.B.: This is _not an accurate state!_ It checks
     * current first and if it's there it does not go ahead and check original! Disambiguation is to be handled by
     * the caller! */
    public Entry get(
            @NonNull final Map<K, Object> original, @NonNull final Map<K, Object> current, @NonNull final K key) {
        final var valueO = original.get(key);
        final var kindO = determineKind(original, key, valueO).rank();
        final var valueC = current.get(key);
        final var kindC = determineKind(current, key, valueC).rank();

        final var state = toValueState[kindO][kindC];
        final var actualValue = toValue.get(kindO).get(kindC).apply(valueO, valueC);

        return switch (state) {
            case INVALID -> invalidStateMarker;
            case NOT_YET_FETCHED -> valueNotYetFetchedMarker;
            case MISSING -> valueMissingMarker;
            case DELETED -> valueDeletedMarker;
            case PRESENT, UPDATED -> new Entry(state, actualValue);
        };
    }

    /** For a given key return the state, as `Kind` enum, of that entry in this cache */
    private Kind determineKind(@NonNull final Map<K, Object> map, @NonNull final K key, final Object value) {
        // Java's `Map` doesn't let you find out in a single call if the value was in the map but `null` or not in the
        // map. So if the value turns out to be null you've got to do a second call to distinguish that.
        if (value != null) return Kind.NON_NULL;
        if (map.containsKey(key)) return Kind.NULL;
        return Kind.MISSING;
    }
    /** Entities (denoted by their key) can be in one of the following states */
    public enum ValueState {
        INVALID, // invalid state
        NOT_YET_FETCHED, // haven't yet tried to read it from upstream cache
        PRESENT, // read from upstream cache, found
        MISSING, // read from upstream cache, wasn't present there
        UPDATED, // has been set (written) in this cache
        DELETED, // has been deleted in this cache
    }
    /** Describes the state of a cache line. Converts two tests of a map entry into a single state description. */
    private enum Kind {
        MISSING(0), // missing from this cache
        NULL(1), // has the actual value `null`
        NON_NULL(2); // is some instance of some type, _not_ `null`

        private final int rank;

        Kind(final int rank) {
            this.rank = rank;
        }

        int rank() {
            return rank;
        }
    }

    public record Entry(@NonNull ValueState state, Object value) {}
}
