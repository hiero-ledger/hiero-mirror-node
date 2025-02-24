// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.rest.model.Topic;
import com.hedera.mirror.restjava.mapper.TopicMapper;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

        @ValueSource(strings = {"1000", "0.1000", "0.0.1000"})
        @ParameterizedTest
        void success(String id) {
            // Given
            var entity = domainBuilder.topicEntity().customize(e -> e.id(1000L)).persist();
            var customFee = domainBuilder
                    .customFee()
                    .customize(c -> c.entityId(1000L)
                            .fractionalFees(null)
                            .royaltyFees(null)
                            .timestampRange(entity.getTimestampRange()))
                    .persist();
            var topic = domainBuilder
                    .topic()
                    .customize(t -> t.createdTimestamp(entity.getCreatedTimestamp())
                            .id(1000L)
                            .timestampRange(entity.getTimestampRange()))
                    .persist();

            // When
            var response = restClient.get().uri("", id).retrieve().toEntity(Topic.class);

            // Then
            assertThat(response.getBody()).isNotNull().isEqualTo(topicMapper.map(customFee, entity, topic));
            // Based on application.yml response headers configuration
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=5");
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
            validateError(callable, HttpClientErrorException.BadRequest.class, "Invalid value for path variable 'id'");
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
            validateError(
                    callable, HttpClientErrorException.NotFound.class, "Entity not found: " + entity.toEntityId());
        }

        @ParameterizedTest
        @ValueSource(strings = {"999", "0.999", "0.0.999"})
        void entityNotFound(String id) {
            // When
            ThrowingCallable callable =
                    () -> restClient.get().uri("", id).retrieve().body(Topic.class);

            // Then
            validateError(callable, HttpClientErrorException.NotFound.class, "Entity not found: 0.0.999");
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
