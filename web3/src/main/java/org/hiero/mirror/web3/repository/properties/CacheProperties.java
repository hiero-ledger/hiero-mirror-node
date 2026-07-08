// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository.properties;

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

    /**
     * Holds hashes of {@code (callData, receiver)} combinations whose requests accessed enough contract storage slots
     * (see {@link #contractStorageDiscoveryThreshold}) to warrant running the storage discovery pass on subsequent
     * identical requests.
     */
    @NotBlank
    private String contractStorageDiscovery = "expireAfterWrite=10m,maximumSize=1000,recordStats";

    /**
     * The number of contract storage slot reads a request must exceed before it is flagged as a storage discovery
     * candidate.
     */
    private int contractStorageDiscoveryThreshold = 15;

    private boolean enableBatchContractSlotCaching = true;

    @NotBlank
    private String entity = ENTITY_CACHE_CONFIG;

    /**
     * The maximum number of contract storage slot keys loaded (and searched) in a single {@code findStorageBatch}
     * query. This caps both how many discovered slot keys are consumed per batch and how many cached slot keys are
     * fetched from the database at once.
     */
    private int maxSlotKeysPerBatch = 200;

    @NotBlank
    private String fee = "expireAfterWrite=60m,maximumSize=20,recordStats";

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
