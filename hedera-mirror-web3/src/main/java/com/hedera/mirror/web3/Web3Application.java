// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3;

import com.hedera.mirror.common.CommonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import(CommonConfiguration.class)
@SpringBootApplication
public class Web3Application {

    public static void main(String[] args) {
        SpringApplication.run(Web3Application.class, args);
    }
}
