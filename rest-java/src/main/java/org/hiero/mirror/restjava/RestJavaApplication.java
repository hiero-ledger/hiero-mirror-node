// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava;

import org.hiero.mirror.common.CommonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import(CommonConfiguration.class)
@SpringBootApplication
public class RestJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestJavaApplication.class, args);
    }
}
