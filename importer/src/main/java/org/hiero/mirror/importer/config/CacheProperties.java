// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "hiero.mirror.importer.cache")
public class CacheProperties {

    @NotBlank
    private String addressBook = "maximumSize=100,expireAfterWrite=5m,recordStats";

    @NotBlank
    private String alias = "maximumSize=100000,expireAfterAccess=30m,recordStats";

    // Connector commitments are permanent once assigned (HIP-1535), so no expiry is needed
    @NotBlank
    private String clprConnectorCommitment = "maximumSize=1000,recordStats";

    // Only one entry is ever cached: the last known full endpoint manifest, used as the merge baseline for partial
    // singleton state changes
    @NotBlank
    private String clprEndpointManifest = "maximumSize=1,recordStats";

    // Only one entry is ever cached: the last known full ledger configuration, used as the merge baseline for
    // partial singleton state changes
    @NotBlank
    private String clprLedgerConfiguration = "maximumSize=1,recordStats";

    private boolean enabled = true;

    @NotBlank
    private String evmAddress = "maximumSize=500000,recordStats";

    @NotBlank
    private String fileData = "maximumSize=100,expireAfterAccess=5m,recordStats";

    @NotBlank
    private String timePartition = "maximumSize=50,expireAfterWrite=1d,recordStats";

    @NotBlank
    private String timePartitionOverlap = "maximumSize=50,expireAfterWrite=1d,recordStats";
}
