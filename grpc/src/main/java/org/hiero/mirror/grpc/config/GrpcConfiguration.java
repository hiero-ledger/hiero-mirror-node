// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hiero.mirror.grpc.GrpcProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.ServerBuilderCustomizer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
class GrpcConfiguration {

    @Bean
    @Qualifier("readOnly")
    TransactionOperations transactionOperationsReadOnly(PlatformTransactionManager transactionManager) {
        var transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
        return transactionTemplate;
    }

    @Bean(destroyMethod = "close")
    Executor grpcVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ServerBuilderCustomizer<NettyServerBuilder> grpcServerConfigurer(
            Executor grpcVirtualThreadExecutor, GrpcProperties grpcProperties) {
        final var nettyProperties = grpcProperties.getNetty();

        return serverBuilder -> {
            if (serverBuilder instanceof NettyServerBuilder nettyServerBuilder) {
                nettyServerBuilder
                        .executor(grpcVirtualThreadExecutor)
                        .maxConnectionIdle(
                                nettyProperties.getMaxConnectionIdle().toSeconds(), TimeUnit.SECONDS)
                        .maxConcurrentCallsPerConnection(nettyProperties.getMaxConcurrentCallsPerConnection())
                        .maxInboundMessageSize(nettyProperties.getMaxInboundMessageSize())
                        .maxInboundMetadataSize(nettyProperties.getMaxInboundMetadataSize());
            }
        };
    }
}
