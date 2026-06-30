// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hiero.mirror.web3.prestate.tracer")
@Data
public class PrestateProperties {
    private boolean enabled = false;
}
