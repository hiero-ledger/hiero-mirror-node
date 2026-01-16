// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.rest.model.Topic;
import org.hiero.mirror.restjava.mapper.TopicMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
class TopicControllerTest extends ControllerTest {

    private final TopicMapper topicMapper;

    @DisplayName("/api/v1/topics/{id}")
    @Nested
    class TopicIdEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "topics/{id}";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            var entity = domainBuilder.topicEntity().persist();
            domainBuilder
                    .customFee()
                    .customize(c -> c.entityId(entity.getId())
                            .fractionalFees(null)
                            .royaltyFees(null)
                            .timestampRange(entity.getTimestampRange()))
                    .persist();
            domainBuilder
                    .topic()
                    .customize(t -> t.createdTimestamp(entity.getCreatedTimestamp())
                            .id(entity.getId())
                            .timestampRange(entity.getTimestampRange()))
                    .persist();
            return uriSpec.uri("", entity.toEntityId().toString());
        }

        @TestFactory
        Stream<DynamicTest> success() {
            // Given
            final var entityId = domainBuilder.entityNum(1000);
            final long encodedId = entityId.getId();
            final var entity =
                    domainBuilder.topicEntity().customize(e -> e.id(encodedId)).persist();
            final var customFee = domainBuilder
                    .customFee()
                    .customize(c -> c.entityId(encodedId)
                            .fractionalFees(null)
                            .royaltyFees(null)
                            .timestampRange(entity.getTimestampRange()))
                    .persist();
            final var topic = domainBuilder
                    .topic()
                    .customize(t -> t.createdTimestamp(entity.getCreatedTimestamp())
                            .id(encodedId)
                            .timestampRange(entity.getTimestampRange()))
                    .persist();

            final var inputs = List.of(
                    entityId.toString(),
                    String.format("%d.%d", entityId.getRealm(), entityId.getNum()),
                    Long.toString(entityId.getNum()));
            ThrowingConsumer<String> executor = input -> {
                // When
                final var response = restClient.get().uri("", input).retrieve().toEntity(Topic.class);

                // Then
                assertThat(response.getBody()).isNotNull().isEqualTo(topicMapper.map(customFee, entity, topic));
                // Based on application.yml response headers configuration
                assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
                assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=5");
            };

            return DynamicTest.stream(inputs.iterator(), Function.identity(), executor);
        }

        @ValueSource(
                strings = {
                    "AABBCC22",
                    " 0.0.1 ",
                    "0.0.0.2",
                    "a.b.c",
                    "a.0.1000",
                    ".0.1000",
                    "-1",
                    "000000000000000000000000000000000186Fb1b",
                    "9223372036854775807" // Long.MAX_VALUE
                })
        @ParameterizedTest
        void invalidId(String id) {
            // When
            ThrowingCallable callable =
                    () -> restClient.get().uri("", id).retrieve().body(Topic.class);

            // Then
            validateError(callable, HttpClientErrorException.BadRequest.class, "Failed to convert 'id'");
        }

        @Test
        void invalidType() {
            // Given
            var entity = domainBuilder
                    .entity()
                    .customize(e -> e.type(EntityType.ACCOUNT))
                    .persist();

            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("", entity.toEntityId().toString())
                    .retrieve()
                    .body(Topic.class);

            // Then
            validateError(callable, HttpClientErrorException.NotFound.class, "Topic not found");
        }

        @TestFactory
        Stream<DynamicTest> entityNotFound() {
            final var entityId = domainBuilder.entityNum(1000);
            final var inputs = List.of(
                    entityId.toString(),
                    String.format("%d.%d", entityId.getRealm(), entityId.getNum()),
                    Long.toString(entityId.getNum()));
            final ThrowingConsumer<String> executor = input -> {
                // When
                ThrowingCallable callable =
                        () -> restClient.get().uri("", input).retrieve().body(Topic.class);

                // Then
                validateError(callable, HttpClientErrorException.NotFound.class, "Topic not found");
            };
            return DynamicTest.stream(inputs.iterator(), Function.identity(), executor);
        }

        @NullAndEmptySource
        @ParameterizedTest
        void notFound(String id) {
            // When
            ThrowingCallable callable =
                    () -> restClient.get().uri("", id).retrieve().body(Topic.class);

            // Then
            validateError(callable, HttpClientErrorException.NotFound.class, "No static resource api/v1/topics.");
        }
    }
}
