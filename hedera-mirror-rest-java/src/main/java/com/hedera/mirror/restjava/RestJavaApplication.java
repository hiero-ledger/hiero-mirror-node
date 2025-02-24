// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@EntityScan("com.hedera.mirror.common.domain")
@SpringBootApplication
public class RestJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestJavaApplication.class, args);
    }
}
