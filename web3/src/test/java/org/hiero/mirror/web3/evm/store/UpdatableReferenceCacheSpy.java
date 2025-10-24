// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;

/**
 * Test spy that implements an UpdatableReferenceCache with K = String. Only new
 * behavior added - **no existing behavior overridden** so you can be sure it
 * doesn't _change_ the behavior of the UpdatableReferenceCache you're testing. It
 * merely allows you to set/inspect the internal state at will.
 */
public class UpdatableReferenceCacheSpy extends UpdatableReferenceCache<String> {

    public static final Class<Long> VALUE_CLASS = Long.class;

    @NonNull
    public UpdatableReferenceCacheSpy addToOriginal(@NonNull final String k, final long v) {
        original.put(k, v);
        return this;
    }

    @NonNull
    public UpdatableReferenceCacheSpy addNullToOriginal(@NonNull final String k) {
        original.put(k, null);
        return this;
    }

    @NonNull
    public UpdatableReferenceCacheSpy clearOriginal() {
        original.clear();
        return this;
    }

    @NonNull
    public Map<String, Object> getOriginal() {
        return original;
    }

    @NonNull
    public UpdatableReferenceCacheSpy addToCurrent(@NonNull final String k, final long v) {
        current.put(k, v);
        return this;
    }

    @NonNull
    public UpdatableReferenceCacheSpy addNullToCurrent(@NonNull final String k) {
        current.put(k, null);
        return this;
    }

    @NonNull
    public UpdatableReferenceCacheSpy clearCurrent() {
        current.clear();
        return this;
    }

    @NonNull
    public Map<String, Object> getCurrent() {
        return current;
    }

    /** Returns (for tests) a triple of #original accounts, #added accounts, #deleted accounts */
    public @NonNull Counts getCounts() {
        return Counts.of(
                original.size(),
                (int) current.values().stream().filter(Objects::nonNull).count(),
                (int) current.values().stream().filter(Objects::isNull).count());
    }

    /**
     * Returns the current cache state for reads (from parent caches) or the current adds or the
     * current deletes
     */
    public @NonNull Map<String, Long> getCacheState(@NonNull final Type type) {
        Objects.requireNonNull(type, "type");

        final Function<Map<String, Object>, Map<String, Long>> copyAndCast =
                m -> m.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, VALUE_CLASS::cast));

        return switch (type) {
            case READ_ONLY -> copyAndCast.apply(original);
            case UPDATED -> copyAndCast.apply(current);
            case CURRENT_STATE -> {
                final var r = copyAndCast.apply(original);
                current.forEach((k, v) -> r.put(k, (Long) v));
                yield r;
            }
        };
    }

    public enum Type {
        READ_ONLY,
        UPDATED,
        CURRENT_STATE
    }

    public record Counts(int read, int updated, int deleted) {
        public static Counts of(int r, int u, int d) {
            return new Counts(r, u, d);
        }
    }
}
