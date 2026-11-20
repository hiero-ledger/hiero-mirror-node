// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "hiero.mirror.web3.cache")
public class CacheProperties {

    /**
     * entity, contract and token models are used mostly simultaneously, so we use the same configuration for consistent
     * reads
     */
    private static final String ENTITY_CACHE_CONFIG = "expireAfterWrite=1s,maximumSize=10000,recordStats";

    @NotBlank
    private String contract = "expireAfterAccess=1h,maximumSize=3000,recordStats";

    @NotBlank
    private String contractSlots = "expireAfterAccess=5m,maximumSize=3000,recordStats";

    @NotBlank
    private String contractState = "expireAfterWrite=2s,maximumSize=25000,recordStats";

    private boolean enableBatchContractSlotCaching = true;

    @NotBlank
    private String entity = ENTITY_CACHE_CONFIG;

    @NotBlank
    private String fee = "expireAfterWrite=60m,maximumSize=20,recordStats";

    /**
     * Cache specification for each historical entity, token account and balance lookup. Expiry time is set to 1 min
     * because most of the repeat lookups, in practice, arrive within seconds, at the same time a short expiry makes
     * the cache harder to hold full. The initial capacity is pre-sized in order to minimize concurrent misses on
     * unrelated keys queue behind each other's queries.
     */
    @NotBlank
    private String historical = "expireAfterWrite=1m,initialCapacity=5000,maximumSize=5000,recordStats";

    /**
     * Cache specification for historical contract storage slot lookups.
     */
    @NotBlank
    private String historicalContractState = "expireAfterWrite=1m,initialCapacity=50000,maximumSize=50000,recordStats";

    /**
     * Cache specification for historical token lookups. A token holds up to seven keys, each up to the
     * transaction size, so one entry can reach tens of kilobytes. The smaller size bounds the memory a client can fill
     * by querying one large token at many blocks.
     */
    @NotBlank
    private String historicalToken = "expireAfterWrite=1m,initialCapacity=1000,maximumSize=1000,recordStats";

    @Min(1)
    @Max(10000)
    private int maxSlotKeysPerBatch = 100;

    @NotBlank
    private String sharedWritableState = "expireAfterAccess=5m,maximumSize=100000,recordStats";

    @NotBlank
    private String slotsPerContract = "expireAfterAccess=5m,maximumSize=1500";

    @NotBlank
    private String systemAccount = "expireAfterWrite=10m,maximumSize=1000,recordStats";

    @NotBlank
    private String systemFile = "expireAfterWrite=10m,maximumSize=20,recordStats";

    @NotBlank
    private String token = ENTITY_CACHE_CONFIG;

    @NotBlank
    private String tokenType = "expireAfterAccess=24h,maximumSize=100000,recordStats";
}
