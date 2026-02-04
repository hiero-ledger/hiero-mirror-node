// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@AutoConfigureAfter(DataRedisAutoConfiguration.class)
@Configuration
public class RedisTestConfiguration {
    @Bean
    @ServiceConnection("redis")
    GenericContainer<?> redis() {
        var logger = LoggerFactory.getLogger("RedisContainer");
        return new GenericContainer<>(DockerImageName.parse("redis:7.4"))
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
                .withExposedPorts(6379)
                .withLogConsumer(new Slf4jLogConsumer(logger, true));
    }
}
