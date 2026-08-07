// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository.properties;

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
    private String contract = "expireAfterAccess=1h,maximumSize=1000,recordStats";

    @NotBlank
    private String contractSlots = "expireAfterAccess=5m,maximumSize=3000,recordStats";

    @NotBlank
    private String contractState = "expireAfterWrite=2s,maximumSize=25000,recordStats";

    @NotBlank
    private String contractStateHistorical = "expireAfterAccess=10m,maximumSize=25000,recordStats";

    private boolean enableBatchContractSlotCaching = true;

    @NotBlank
    private String entity = ENTITY_CACHE_CONFIG;

    /**
     * The maximum number of contract storage slot keys loaded (and searched) in a single {@code findStorageBatch}
     * query.
     */
    @Min(1)
    private int maxSlotKeysPerBatch = 100;

    /**
     * The maximum number of slot keys tracked per contract for batch prefetch. Oldest keys are dropped when the limit
     * is exceeded.
     */
    @Min(1)
    private int maxSlotsPerContract = 1500;

    @NotBlank
    private String fee = "expireAfterWrite=60m,maximumSize=20,recordStats";

    @NotBlank
    private String sharedWritableState = "expireAfterAccess=5m,maximumSize=100000,recordStats";

    @NotBlank
    private String systemAccount = "expireAfterWrite=10m,maximumSize=1000,recordStats";

    @NotBlank
    private String systemFile = "expireAfterWrite=10m,maximumSize=20,recordStats";

    @NotBlank
    private String token = ENTITY_CACHE_CONFIG;

    @NotBlank
    private String tokenType = "expireAfterAccess=24h,maximumSize=100000,recordStats";
}
