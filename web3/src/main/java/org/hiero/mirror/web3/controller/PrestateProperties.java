// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "hiero.mirror.web3.prestate")
@Data
@Validated
public class PrestateProperties {

    /**
     * Maximum total runtime bytecode bytes included in a prestate response. Requests that would exceed this
     * limit fail with HTTP 400.
     */
    @Positive
    private int maxBytecodeBytes = 10_000_000;

    /**
     * Maximum number of touched accounts included in a prestate response. Additional accounts are omitted.
     */
    @Positive
    private int maxTouchedAccounts = 1000;

    /**
     * Maximum number of contract-state-change pages fetched for a prestate request. Further pages are not loaded
     * once this limit is reached.
     */
    @Positive
    private int stateChangeMaxPages = 10;

    /**
     * Maximum number of contract state changes fetched per page. Pagination stops when a page has fewer than
     * this many rows.
     */
    @Positive
    private int stateChangePageSize = 5000;
}
