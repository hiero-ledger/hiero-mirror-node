// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.SneakyThrows;

final class FeeEstimationContext {

    enum CacheEntityType {
        TOKEN,
        TOPIC
    }

    static final int MAX_LOOKUPS = 32;

    private static final ScopedValue<FeeEstimationContext> SCOPED_VALUE = ScopedValue.newInstance();

    private final Map<CacheEntityType, Map<Object, Object>> readCache = new EnumMap<>(CacheEntityType.class);

    private FeeEstimationContext() {}

    static FeeEstimationContext get() {
        return SCOPED_VALUE.get();
    }

    @SneakyThrows
    static <T> T run(final Function<FeeEstimationContext, T> function) {
        return ScopedValue.where(SCOPED_VALUE, new FeeEstimationContext())
                .call(() -> function.apply(SCOPED_VALUE.get()));
    }

    Map<Object, Object> getReadCache(final CacheEntityType cacheEntityType) {
        return readCache.computeIfAbsent(cacheEntityType, _ -> new HashMap<>());
    }

    int readCount() {
        var count = 0;
        for (final var cache : readCache.values()) {
            count += cache.size();
        }
        return count;
    }
}
