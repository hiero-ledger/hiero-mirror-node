// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.grpc;

import com.hedera.mirror.common.CommonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import(CommonConfiguration.class)
@SpringBootApplication
public class GrpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrpcApplication.class, args);
    }
}
