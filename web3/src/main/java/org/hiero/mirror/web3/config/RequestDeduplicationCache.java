// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.util.concurrent.TimeUnit;

@Named
public class RequestDeduplicationCache {

    private Cache<String, Long> requestCache;
    private static final int CACHE_ENTRY_TTL = 2;
    private static final int CACHE_ENTRIES_LIMIT = 1000;

    @PostConstruct
    public void init() {
        requestCache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_ENTRY_TTL, TimeUnit.SECONDS)
                .maximumSize(CACHE_ENTRIES_LIMIT)
                .build();
    }

    public boolean isDuplicate(String key) {
        Long lastSeen = requestCache.getIfPresent(key);
        if (lastSeen != null) {
            return true;
        }
        requestCache.put(key, System.currentTimeMillis());
        return false;
    }
}
