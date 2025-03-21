// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.throttle;

import com.hedera.node.app.throttle.ThrottleParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThrottleConfig {

    @Bean
    public ThrottleParser customThrottleParser() {
        return new ThrottleParser();
    }
}
