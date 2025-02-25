// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@EntityScan("com.hedera.mirror.common.domain")
@SpringBootApplication
public class Web3Application {

    public static void main(String[] args) {
        SpringApplication.run(Web3Application.class, args);
    }
}
