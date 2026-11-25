// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import java.util.Map;
import java.util.function.Supplier;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.jspecify.annotations.Nullable;

/**
 * The value a singleton state has read and the value it was modified to, kept in the caches of the current request in
 * {@link ContractCallContext}.
 */
@SuppressWarnings("unchecked")
final class SingletonRequestCache {

    private static final Object KEY = new Object();
    private static final Object NULL = new Object();

    private SingletonRequestCache() {}

    static boolean isModified(final int stateId) {
        return writeCache(stateId).containsKey(KEY);
    }

    static boolean isRead(final int stateId) {
        return readCache(stateId).containsKey(KEY);
    }

    /** Returns the value the singleton was modified to, or null when it is unmodified or was modified to null. */
    static <T> @Nullable T modification(final int stateId) {
        final var modification = writeCache(stateId).get(KEY);
        return modification == NULL ? null : (T) modification;
    }

    static void modify(final int stateId, final @Nullable Object value) {
        writeCache(stateId).put(KEY, value == null ? NULL : value);
    }

    /** Returns the value read from the data source, which is read once per request. */
    static <T> @Nullable T read(final int stateId, final Supplier<T> dataSource) {
        final var cache = readCache(stateId);
        final var cached = cache.get(KEY);
        if (cached != null) {
            return cached == NULL ? null : (T) cached;
        }
        final var value = dataSource.get();
        cache.put(KEY, value == null ? NULL : value);
        return value;
    }

    static void resetModification(final int stateId) {
        writeCache(stateId).clear();
    }

    static void resetRead(final int stateId) {
        readCache(stateId).clear();
    }

    private static Map<Object, Object> readCache(final int stateId) {
        return ContractCallContext.get().getReadCacheState(stateId);
    }

    private static Map<Object, Object> writeCache(final int stateId) {
        return ContractCallContext.get().getWriteCacheState(stateId);
    }
}
