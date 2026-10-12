// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import io.grpc.netty.NettyServerBuilder;
import java.util.concurrent.Executor;
import org.hiero.mirror.grpc.GrpcProperties;
import org.hiero.mirror.grpc.service.AddressBookProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.ServerBuilderCustomizer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration(proxyBeanMethods = false)
class GrpcConfiguration {

    // An active subscription queues its subscribe task, its timeout timer and its next page delay on the scheduler.
    // Tasks aren't balanced by queue length across threads, so each thread's queue must fit all subscriptions.
    private static final int TASKS_PER_SUBSCRIPTION = 3;

    @Bean(destroyMethod = "dispose")
    @Qualifier("addressBook")
    Scheduler addressBookScheduler(AddressBookProperties addressBookProperties) {
        final int requiredQueueSize = addressBookProperties.getMaxConcurrent() * TASKS_PER_SUBSCRIPTION;
        final int queueSize = Math.max(addressBookProperties.getSchedulerQueueSize(), requiredQueueSize);
        return Schedulers.newBoundedElastic(addressBookProperties.getSchedulerSize(), queueSize, "address-book");
    }

    @Bean
    @Qualifier("readOnly")
    TransactionOperations transactionOperationsReadOnly(PlatformTransactionManager transactionManager) {
        var transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
        return transactionTemplate;
    }

    @Bean
    ServerBuilderCustomizer<NettyServerBuilder> grpcServerConfigurer(
            GrpcProperties grpcProperties, Executor applicationTaskExecutor) {
        final var nettyProperties = grpcProperties.getNetty();
        return serverBuilder -> {
            serverBuilder.executor(applicationTaskExecutor);
            serverBuilder.maxConcurrentCallsPerConnection(nettyProperties.getMaxConcurrentCallsPerConnection());
        };
    }
}
