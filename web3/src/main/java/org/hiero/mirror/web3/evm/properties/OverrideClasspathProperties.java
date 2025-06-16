// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.properties;

import jakarta.annotation.PostConstruct;
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Value
@Validated
@ConfigurationProperties(prefix = "hiero.mirror.web3.evm.classpath")
public class OverrideClasspathProperties {

    public static final String ALLOW_LONG_ZERO_ADDRESSES = "ALLOW_LONG_ZERO_ADDRESSES";

    boolean allowLongZeroAddresses = false;

    @PostConstruct
    public void init() {
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(allowLongZeroAddresses));
    }
}
