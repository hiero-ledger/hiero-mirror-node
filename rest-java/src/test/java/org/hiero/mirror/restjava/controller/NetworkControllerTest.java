// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.hiero.mirror.restjava.mapper.NetworkStakeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
class NetworkControllerTest extends ControllerTest {

    private final NetworkStakeMapper networkStakeMapper;

    @DisplayName("/api/v1/network/stake")
    @Nested
    class NetworkStakeEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "network/stake";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            domainBuilder.networkStake().persist();
            return uriSpec.uri("");
        }

        @Test
        void returnsLatestStake() {
            // given
            final var networkStake = domainBuilder.networkStake().persist();
            final var expected = networkStakeMapper.map(networkStake);

            // when
            final var actual = restClient.get().uri("").retrieve().body(NetworkStakeResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void returnsNotFoundWhenNoData() {
            validateError(
                    () -> restClient.get().uri("").retrieve().toEntity(String.class),
                    HttpClientErrorException.NotFound.class,
                    "No network stake data found");
        }
    }
}
