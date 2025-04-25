// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.hiero.mirror.grpc.GrpcProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    GrpcServerConfigurer grpcServerConfigurer(GrpcProperties grpcProperties) {
        NettyProperties nettyProperties = grpcProperties.getNetty();
        return serverBuilder -> customizeServerBuilder(serverBuilder, nettyProperties);
    }

    private void customizeServerBuilder(ServerBuilder<?> serverBuilder, NettyProperties nettyProperties) {
        if (serverBuilder instanceof NettyServerBuilder nettyServerBuilder) {
            Executor executor = new ThreadPoolExecutor(
                    nettyProperties.getExecutorCoreThreadCount(),
                    nettyProperties.getExecutorMaxThreadCount(),
                    nettyProperties.getThreadKeepAliveTime().toSeconds(),
                    TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    new ThreadFactoryBuilder()
                            .setDaemon(true)
                            .setNameFormat("grpc-executor-%d")
                            .build());

            nettyServerBuilder
                    .executor(executor)
                    .maxConnectionIdle(nettyProperties.getMaxConnectionIdle().toSeconds(), TimeUnit.SECONDS)
                    .maxConcurrentCallsPerConnection(nettyProperties.getMaxConcurrentCallsPerConnection())
                    .maxInboundMessageSize(nettyProperties.getMaxInboundMessageSize())
                    .maxInboundMetadataSize(nettyProperties.getMaxInboundMetadataSize());
        }
    }
}
