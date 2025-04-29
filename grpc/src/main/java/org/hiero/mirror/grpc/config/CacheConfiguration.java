// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Set;
import org.hiero.mirror.grpc.GrpcProperties;
import org.hiero.mirror.grpc.service.AddressBookProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(prefix = "spring.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableCaching
public class CacheConfiguration {

    public static final String ADDRESS_BOOK_ENTRY_CACHE = "addressBookEntryCache";
    public static final String NODE_STAKE_CACHE = "nodeStakeCache";
    public static final String ENTITY_CACHE = "entityCache";
    public static final String CACHE_NAME = "default";

    @Bean(ADDRESS_BOOK_ENTRY_CACHE)
    CacheManager addressBookEntryCache(AddressBookProperties addressBookProperties) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME)); // We have to eagerly set cache name to register metrics
        caffeineCacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(addressBookProperties.getCacheExpiry())
                .maximumSize(addressBookProperties.getCacheSize())
                .recordStats());
        return caffeineCacheManager;
    }

    @Bean(NODE_STAKE_CACHE)
    CacheManager nodeStakeCache(AddressBookProperties addressBookProperties) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME));
        caffeineCacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(addressBookProperties.getNodeStakeCacheExpiry())
                .maximumSize(addressBookProperties.getNodeStakeCacheSize())
                .recordStats());
        return caffeineCacheManager;
    }

    @Bean(ENTITY_CACHE)
    @Primary
    CacheManager entityCache(GrpcProperties grpcProperties) {
        int cacheSize = grpcProperties.getEntityCacheSize();
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(CACHE_NAME));
        caffeineCacheManager.setCacheSpecification("recordStats,expireAfterWrite=24h,maximumSize=" + cacheSize);
        return caffeineCacheManager;
    }
}
