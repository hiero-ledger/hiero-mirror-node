// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "hiero.mirror.web3.evm.classpath")
public class OverrideClasspathProperties {

    private boolean overridePayerBalanceValidation;
}
