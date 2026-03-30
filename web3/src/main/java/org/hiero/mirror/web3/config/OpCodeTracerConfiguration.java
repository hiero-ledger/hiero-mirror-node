// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hiero.mirror.web3.opcode.tracer")
@Data
public class OpCodeTracerConfiguration {
    private boolean enabled = false;
}
