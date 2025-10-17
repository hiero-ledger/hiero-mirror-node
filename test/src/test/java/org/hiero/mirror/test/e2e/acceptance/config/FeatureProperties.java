// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.config;

import jakarta.inject.Named;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Named
@ConfigurationProperties(prefix = "hiero.mirror.test.acceptance.feature")
@Data
@RequiredArgsConstructor
@Validated
public class FeatureProperties {

    private boolean contractCallLocalEstimate = true;

    private int hapiMinorVersionWithoutGasRefund = 67;

    @Min(1)
    @Max(10_000_000)
    private long maxContractFunctionGas = 5_250_000;

    private boolean sidecars = false;
}
