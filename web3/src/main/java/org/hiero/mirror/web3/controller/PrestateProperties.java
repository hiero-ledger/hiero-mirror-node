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

    private boolean enabled = false;

    @Positive
    private int maxTouchedAccounts = 1000;

    @Positive
    private int stateChangeMaxPages = 10;

    @Positive
    private int stateChangePageSize = 5000;
}
