// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.config;

import static org.hiero.mirror.test.e2e.acceptance.config.RestProperties.URL_PREFIX;

import jakarta.inject.Named;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "hiero.mirror.test.acceptance.rest-java")
@Data
@Named
@RequiredArgsConstructor
@Validated
public class RestJavaProperties {

    private String baseUrl;

    private boolean enabled = false;

    @NotNull
    @Valid
    private FeeEstimation feeEstimation = new FeeEstimation();

    public String getBaseUrl() {
        if (baseUrl != null && !baseUrl.endsWith(URL_PREFIX)) {
            return baseUrl + URL_PREFIX;
        }
        return baseUrl;
    }

    /**
     * Controls verification of the fees charged for each executed HAPI transaction against the estimate returned by the
     * REST Java {@code POST /api/v1/network/fees} endpoint before the transaction is submitted.
     */
    @Data
    public static class FeeEstimation {

        /** Whether to estimate the fee of each HAPI transaction before executing it and verify the charged fee. */
        private boolean enabled = false;

        /** Whether the estimate is based solely on the transaction's inherent properties or also on network state. */
        @NotNull
        private FeeEstimateMode mode = FeeEstimateMode.INTRINSIC;

        /**
         * How far, in percent, the fee actually charged for a transaction may deviate from the estimated fee converted
         * to tinybars. Estimates are not expected to be exact in all cases: the charged fee is converted from
         * tinycents using the exchange rate effective at consensus time and is subject to rounding.
         */
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        private double tolerancePercent = 5.0;
    }
}
