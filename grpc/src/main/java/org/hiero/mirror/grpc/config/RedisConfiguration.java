// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.hiero.mirror.common.converter.EntityIdDeserializer;
import org.hiero.mirror.common.converter.EntityIdSerializer;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@AutoConfigureBefore(RedisAutoConfiguration.class)
@AutoConfigureAfter({MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class})
@Configuration
class RedisConfiguration {

    @Bean
    RedisSerializer<TopicMessage> redisSerializer() {
        var module = new SimpleModule();
        module.addDeserializer(EntityId.class, EntityIdDeserializer.INSTANCE);
        module.addSerializer(EntityIdSerializer.INSTANCE);

        var objectMapper = new ObjectMapper(new MessagePackFactory());
        objectMapper.registerModule(module);
        return new Jackson2JsonRedisSerializer<>(objectMapper, TopicMessage.class);
    }

    @Bean
    ReactiveRedisOperations<String, TopicMessage> reactiveRedisOperations(
            ReactiveRedisConnectionFactory connectionFactory) {
        var serializationContext = RedisSerializationContext.<String, TopicMessage>newSerializationContext()
                .key(StringRedisSerializer.UTF_8)
                .value(redisSerializer())
                .hashKey(StringRedisSerializer.UTF_8)
                .hashValue(redisSerializer())
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
