// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc;

import org.hiero.mirror.common.config.CommonIntegrationTest;
import org.hiero.mirror.common.config.RedisTestConfiguration;
import org.hiero.mirror.grpc.GrpcIntegrationTest.Configuration;
import org.hiero.mirror.grpc.config.GrpcTestConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Import({Configuration.class, RedisTestConfiguration.class, GrpcTestConfiguration.class})
public abstract class GrpcIntegrationTest extends CommonIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        @Primary
        TransactionOperations transactionOperations(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }
}
