// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc;

import org.hiero.mirror.common.CommonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

@EnableJdbcRepositories
@Import(CommonConfiguration.class)
@SpringBootApplication
public class GrpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrpcApplication.class, args);
    }
}
