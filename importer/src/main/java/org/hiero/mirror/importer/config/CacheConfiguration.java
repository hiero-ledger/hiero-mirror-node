// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfiguration {

    public static final String CACHE_ADDRESS_BOOK = "addressBook";
    public static final String CACHE_ALIAS = "alias";
    public static final String CACHE_TIME_PARTITION_OVERLAP = "timePartitionOverlap";
    public static final String CACHE_TIME_PARTITION = "timePartition";
    public static final String CACHE_NAME = "default";

    private final CacheProperties cacheProperties;

    @Bean(CACHE_ADDRESS_BOOK)
    @Primary
    CacheManager cacheManagerAddressBook() {
        var cacheManager = cacheManager(cacheProperties.getAddressBook());
        return new TransactionAwareCacheManagerProxy(cacheManager);
    }

    @Bean(CACHE_ALIAS)
    CacheManager cacheManagerAlias() {
        return cacheManager(cacheProperties.getAlias());
    }

    @Bean(CACHE_TIME_PARTITION)
    CacheManager cacheManagerTimePartition() {
        return cacheManager(cacheProperties.getTimePartition());
    }

    @Bean(CACHE_TIME_PARTITION_OVERLAP)
    CacheManager cacheManagerTimePartitionOverlap() {
        return cacheManager(cacheProperties.getTimePartitionOverlap());
    }

    private CacheManager cacheManager(String specification) {
        if (!cacheProperties.isEnabled()) {
            return new NoOpCacheManager();
        }

        var cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Set.of(CACHE_NAME));
        cacheManager.setCacheSpecification(specification);
        return cacheManager;
    }
}
