// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static com.hedera.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.BaseEncoding;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.rest.model.Links;
import com.hedera.mirror.rest.model.TokenAirdropsResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hiero.mirror.restjava.mapper.TokenAirdropMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
class TokenAirdropsControllerTest extends ControllerTest {

    private final TokenAirdropMapper mapper;

    private TokenAirdropsResponse getExpectedResponse(List<TokenAirdrop> tokenAirdrops, String next) {
        return new TokenAirdropsResponse().airdrops(mapper.map(tokenAirdrops)).links(new Links().next(next));
    }

    @DisplayName("/api/v1/accounts/{id}/airdrops/outstanding")
    @Nested
    class OutstandingTokenAirdropsEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "accounts/{id}/airdrops/outstanding";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
            return uriSpec.uri("", tokenAirdrop.getSenderAccountId());
        }

        @ValueSource(strings = {"1000", "0.1000", "0.0.1000"})
        @ParameterizedTest
        void entityId(String id) {
            // Given
            var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(1000L))
                    .persist();

            // When
            var response = restClient.get().uri("", id).retrieve().toEntity(TokenAirdropsResponse.class);

            // Then
            assertThat(response.getBody().getAirdrops().getFirst()).isEqualTo(mapper.map(tokenAirdrop));
            // Based on application.yml response headers configuration
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=1");
        }

        @Test
        void evmAddress() {
            // Given
            var entity = domainBuilder.entity().persist();
            var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId()))
                    .persist();

            // When
            var response = restClient
                    .get()
                    .uri("", DomainUtils.bytesToHex(entity.getEvmAddress()))
                    .retrieve()
                    .toEntity(TokenAirdropsResponse.class);

            // Then
            assertThat(response.getBody().getAirdrops().getFirst()).isEqualTo(mapper.map(tokenAirdrop));
        }

        @Test
        void alias() {
            // Given
            var entity = domainBuilder.entity().persist();
            var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId()))
                    .persist();

            // When
            var response = restClient
                    .get()
                    .uri("", BaseEncoding.base32().omitPadding().encode(entity.getAlias()))
                    .retrieve()
                    .toEntity(TokenAirdropsResponse.class);

            // Then
            assertThat(response.getBody().getAirdrops().getFirst()).isEqualTo(mapper.map(tokenAirdrop));
        }

        @Test
        void fungibleFollowDescendingOrderLink() {
            // Given
            long sender = 1000;
            long receiver = 2000;
            long token1 = 100;
            long token2 = 300;
            long token3 = 301;

            var airdrop1 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .tokenId(token1))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .tokenId(token2))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .tokenId(token3))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.receiverAccountId(receiver))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.receiverAccountId(receiver))
                    .persist();

            var uriParams = "?limit=1&receiver.id=gte:%s&order=desc".formatted(receiver);
            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(sender);

            // When
            var result = restClient.get().uri(uriParams, sender).retrieve().body(TokenAirdropsResponse.class);
            // The first receiver id is '2000' instead of 0.0.2000 because the link creation does not alter the original
            // value sent in the request
            // The second receiver id is added by the link generator and has shard.realm.num format
            var nextParams = "?limit=1&receiver.id=gte:2000&receiver.id=lte:0.0.2000&order=desc&token.id=lt:0.0.301";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, sender).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = "?limit=1&receiver.id=gte:2000&receiver.id=lte:0.0.2000&order=desc&token.id=lt:0.0.300";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, sender).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = "?limit=1&receiver.id=gte:2000&receiver.id=lte:0.0.2000&order=desc&token.id=lt:0.0.100";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop1), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, sender).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void nftFollowDescendingOrderLink() {
            // Given
            long sender = 1000;
            long receiver = 2000;
            long token1 = 100;
            long token2 = 300;
            long token3 = 301;
            long serial1 = 10;
            long serial2 = 20;
            long serial3 = 30;

            var airdrop1 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .serialNumber(serial1)
                            .tokenId(token1))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .serialNumber(serial2)
                            .tokenId(token2))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .serialNumber(serial3)
                            .tokenId(token3))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.receiverAccountId(receiver))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.receiverAccountId(receiver))
                    .persist();

            var uriParams = "?limit=1&receiver.id=gte:%s&order=desc".formatted(receiver);
            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(sender);

            // When
            var result = restClient.get().uri(uriParams, sender).retrieve().body(TokenAirdropsResponse.class);
            var nextParams =
                    "?limit=1&receiver.id=gte:2000&receiver.id=lte:0.0.2000&order=desc&token.id=lte:0.0.301&serialnumber=lt:30";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, sender).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams =
                    "?limit=1&receiver.id=gte:2000&receiver.id=lte:0.0.2000&order=desc&token.id=lte:0.0.300&serialnumber=lt:20";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, sender).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams =
                    "?limit=1&receiver.id=gte:2000&receiver.id=lte:0.0.2000&order=desc&token.id=lte:0.0.100&serialnumber=lt:10";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop1), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, sender).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void fungibleFollowAscendingOrderLink() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .tokenId(6L))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(4L)
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(entity.getId());
            var nextParams = "?limit=1&receiver.id=gte:0.0.3&token.id=gt:0.0.5";

            // When no primary or secondary parameters are specified
            var uriParams = "?limit=1";
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When primary secondary and tertiary fields are specified
            uriParams = "?limit=1&receiver.id=gt:2&token.id=gte:4&serialnumber=gt:0";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When only the secondary field is specified
            uriParams = "?limit=1&token.id=gt:4";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = "?limit=1&receiver.id=gte:0.0.3&token.id=gt:0.0.6";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = "?limit=1&receiver.id=gte:0.0.4&token.id=gt:0.0.5";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void nftFollowAscendingOrderLink() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .serialNumber(1L)
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .serialNumber(2L)
                            .tokenId(5L))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(4L)
                            .serialNumber(3L)
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(entity.getId());
            var nextParams = "?limit=1&receiver.id=gte:0.0.3&token.id=gte:0.0.5&serialnumber=gt:1";

            // When no primary or secondary parameters are specified
            var uriParams = "?limit=1";
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When primary secondary and tertiary fields are specified
            uriParams = "?limit=1&receiver.id=gt:2&token.id=gte:4&serialnumber=gt:0";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When only the secondary field is specified
            uriParams = "?limit=1&token.id=gt:4";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = "?limit=1&receiver.id=gte:0.0.3&token.id=gte:0.0.5&serialnumber=gt:2";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = "?limit=1&receiver.id=gte:0.0.4&token.id=gte:0.0.5&serialnumber=gt:3";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void allParametersFungible() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(2L)
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .tokenId(5L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .tokenId(6L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(4L)
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(entity.getId());
            var uriParams = "?limit=1&receiver.id=gte:0.0.1&receiver.id=lt:0.0.4&token.id=lte:0.0.5&token.id=gt:0.0.3";

            // When
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams = "?limit=1&receiver.id=lt:0.0.4&receiver.id=gte:0.0.2&token.id=lte:0.0.5&token.id=gt:0.0.5";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = "?limit=1&receiver.id=lt:0.0.4&receiver.id=gte:0.0.3&token.id=lte:0.0.5&token.id=gt:0.0.5";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));
        }

        @Test
        void allParametersNft() {
            var airdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(1000)
                            .receiverAccountId(2000L)
                            .tokenId(4000L)
                            .serialNumber(5))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(1000)
                            .receiverAccountId(2001L)
                            .tokenId(4001L)
                            .serialNumber(100))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(1000)
                            .receiverAccountId(3000L)
                            .tokenId(5000L)
                            .serialNumber(6))
                    .persist();
            var serialOutsideRangeAirdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(1000)
                            .receiverAccountId(2000L)
                            .tokenId(4001L)
                            .serialNumber(1))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a ->
                            a.senderAccountId(1000).receiverAccountId(3001L).tokenId(4001L))
                    .persist();

            var uriParams =
                    "?receiver.id=gte:0.0.2000&receiver.id=lte:0.0.3000&token.id=gte:0.0.4000&token.id=lte:0.0.5000&serialnumber=gte:5&serialnumber=lte:100";
            // When
            var result = restClient.get().uri(uriParams, 1000).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result)
                    .isEqualTo(
                            getExpectedResponse(List.of(airdrop, serialOutsideRangeAirdrop, airdrop2, airdrop3), null));
        }

        @Test
        void allParametersNftFollowLink() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(2L)
                            .serialNumber(10L)
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .serialNumber(20L)
                            .tokenId(5L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .serialNumber(7L)
                            .tokenId(6L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(4L)
                            .serialNumber(8L)
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(entity.getId());
            var uriParams = "?limit=1&receiver.id=gte:0.0.1&receiver.id=lt:0.0.4&token.id=lte:0.0.5&token.id=gt:0.0.3";

            // When
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams =
                    "?limit=1&receiver.id=lt:0.0.4&receiver.id=gte:0.0.2&token.id=lte:0.0.5&token.id=gte:0.0.5&serialnumber=gt:10";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams =
                    "?limit=1&receiver.id=gte:0.0.1&receiver.id=lt:0.0.4&token.id=lte:0.0.5&token.id=gt:0.0.3&serialnumber=gt:5";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&receiver.id=lt:0.0.4&receiver.id=gte:0.0.3&token.id=lte:0.0.5&token.id=gte:0.0.5&serialnumber=gt:20";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));
        }

        @Test
        void allParametersBothTokenTypesFollowAscendingOrder() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(2L)
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .tokenId(5L))
                    .persist();
            var airdropNft = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(2L)
                            .serialNumber(10L)
                            .tokenId(10L))
                    .persist();
            var airdropNft2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(2L)
                            .serialNumber(11L)
                            .tokenId(10L))
                    .persist();
            var airdropNft3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .serialNumber(12L)
                            .tokenId(10L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId() + 1)
                            .receiverAccountId(2L)
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(entity.getId());
            var uriParams = "?limit=1&receiver.id=gte:0.0.1&receiver.id=lt:0.0.4&token.id=lte:0.0.10&token.id=gt:0.0.3";

            // When
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams =
                    "?limit=1&receiver.id=lt:0.0.4&receiver.id=gte:0.0.2&token.id=lte:0.0.10&token.id=gt:0.0.5";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams =
                    "?limit=1&receiver.id=gte:0.0.1&receiver.id=lt:0.0.4&token.id=lte:0.0.10&token.id=gt:0.0.3&serialnumber=gt:5";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When following the next link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&receiver.id=lt:0.0.4&receiver.id=gte:0.0.2&token.id=lte:0.0.10&token.id=gte:0.0.10&serialnumber=gt:10";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&receiver.id=lt:0.0.4&receiver.id=gte:0.0.2&token.id=lte:0.0.10&token.id=gte:0.0.10&serialnumber=gt:11";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft2), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Serial number is removed from the next link
            nextParams = "?limit=1&receiver.id=lt:0.0.4&receiver.id=gte:0.0.3&token.id=lte:0.0.10&token.id=gt:0.0.5";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&receiver.id=lt:0.0.4&receiver.id=gte:0.0.3&token.id=lte:0.0.10&token.id=gte:0.0.10&serialnumber=gt:12";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void allParametersBothTokenTypesFollowDescendingOrder() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(2L)
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .tokenId(5L))
                    .persist();
            var airdropNft = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(2L)
                            .serialNumber(10L)
                            .tokenId(10L))
                    .persist();
            var airdropNft2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(2L)
                            .serialNumber(11L)
                            .tokenId(10L))
                    .persist();
            var airdropNft3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .serialNumber(12L)
                            .tokenId(10L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId() + 1)
                            .receiverAccountId(2L)
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(entity.getId());
            var uriParams =
                    "?limit=1&order=desc&receiver.id=gte:0.0.1&receiver.id=lt:0.0.4&token.id=lte:0.0.10&token.id=gt:0.0.3";

            // When
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams =
                    "?limit=1&order=desc&receiver.id=gte:0.0.1&receiver.id=lte:0.0.3&token.id=gt:0.0.3&token.id=lte:0.0.10&serialnumber=lt:12";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams =
                    "?limit=1&order=desc&receiver.id=gte:0.0.1&receiver.id=lt:0.0.4&token.id=lte:0.0.10&token.id=gt:0.0.3&serialnumber=lt:20";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // When following the next link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Serial number is removed from the next link
            nextParams =
                    "?limit=1&order=desc&receiver.id=gte:0.0.1&receiver.id=lte:0.0.3&token.id=gt:0.0.3&token.id=lt:0.0.5";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&order=desc&receiver.id=gte:0.0.1&receiver.id=lte:0.0.2&token.id=gt:0.0.3&token.id=lte:0.0.10&serialnumber=lt:11";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft2), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&order=desc&receiver.id=gte:0.0.1&receiver.id=lte:0.0.2&token.id=gt:0.0.3&token.id=lte:0.0.10&serialnumber=lt:10";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&order=desc&receiver.id=gte:0.0.1&receiver.id=lte:0.0.2&token.id=gt:0.0.3&token.id=lt:0.0.5";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "0.000000000000000000000000000000000186Fb1b",
                    "0.0.000000000000000000000000000000000186Fb1b",
                    "0x000000000000000000000000000000000186Fb1b",
                    "0.0.AABBCC22",
                    "0.AABBCC22",
                    "AABBCC22"
                })
        void notFound(String accountId) {
            // When
            ThrowingCallable callable =
                    () -> restClient.get().uri("", accountId).retrieve().body(TokenAirdropsResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.NotFound.class, "No account found for the given ID");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "abc",
                    "a.b.c",
                    "0.0.",
                    "0.65537.1001",
                    "0.0.-1001",
                    "9223372036854775807",
                    "0x00000001000000000000000200000000000000034"
                })
        void invalidId(String id) {
            // When
            ThrowingCallable callable =
                    () -> restClient.get().uri("", id).retrieve().body(TokenAirdropsResponse.class);

            // Then
            validateError(
                    callable,
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'id' with value: '" + id + "'");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "abc",
                    "a.b.c",
                    "0.0.",
                    "0.65537.1001",
                    "0.0.-1001",
                    "9223372036854775807",
                    "0x00000001000000000000000200000000000000034"
                })
        void invalidAccountId(String accountId) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?receiver.id={accountId}", "0.0.1001", accountId)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(
                    callable,
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'receiver.id' with value: '" + accountId + "'");
        }

        @ParameterizedTest
        @CsvSource({
            "101, limit must be less than or equal to 100",
            "-1, limit must be greater than 0",
            "a, Failed to convert 'limit' with value: 'a'"
        })
        void invalidLimit(String limit, String expected) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?limit={limit}", "0.0.1001", limit)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.BadRequest.class, expected);
        }

        @ParameterizedTest
        @CsvSource({
            "ascending, Failed to convert 'order' with value: 'ascending'",
            "dsc, Failed to convert 'order' with value: 'dsc'",
            "invalid, Failed to convert 'order' with value: 'invalid'"
        })
        void invalidOrder(String order, String expected) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?order={order}", "0.0.1001", order)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.BadRequest.class, expected);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "abc",
                    "a.b.c",
                    "0.0.",
                    "0.65537.1001",
                    "0.0.-1001",
                    "9223372036854775807",
                    "0x00000001000000000000000200000000000000034"
                })
        void invalidTokenId(String tokenId) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?token.id={tokenId}", "0.0.1001", tokenId)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(
                    callable,
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'token.id' with value: '" + tokenId + "'");
        }
    }

    @DisplayName("/api/v1/accounts/{id}/airdrops/pending")
    @Nested
    class PendingTokenAirdropsEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "accounts/{id}/airdrops/pending";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
            return uriSpec.uri("", tokenAirdrop.getReceiverAccountId());
        }

        @ValueSource(strings = {"1000", "0.1000", "0.0.1000"})
        @ParameterizedTest
        void entityId(String id) {
            // Given
            var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.receiverAccountId(1000L))
                    .persist();

            // When
            var response = restClient.get().uri("", id).retrieve().toEntity(TokenAirdropsResponse.class);

            // Then
            assertThat(response.getBody().getAirdrops().getFirst()).isEqualTo(mapper.map(tokenAirdrop));
            // Based on application.yml response headers configuration
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=1");
        }

        @Test
        void evmAddress() {
            // Given
            var entity = domainBuilder.entity().persist();
            var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.receiverAccountId(entity.getId()))
                    .persist();

            // When
            var response = restClient
                    .get()
                    .uri("", DomainUtils.bytesToHex(entity.getEvmAddress()))
                    .retrieve()
                    .toEntity(TokenAirdropsResponse.class);

            // Then
            assertThat(response.getBody().getAirdrops().getFirst()).isEqualTo(mapper.map(tokenAirdrop));
        }

        @Test
        void alias() {
            // Given
            var entity = domainBuilder.entity().persist();
            var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.receiverAccountId(entity.getId()))
                    .persist();

            // When
            var response = restClient
                    .get()
                    .uri("", BaseEncoding.base32().omitPadding().encode(entity.getAlias()))
                    .retrieve()
                    .toEntity(TokenAirdropsResponse.class);

            // Then
            assertThat(response.getBody().getAirdrops().getFirst()).isEqualTo(mapper.map(tokenAirdrop));
        }

        @Test
        void fungibleFollowDescendingOrderLink() {
            // Given
            long sender = 1000;
            long receiver = 2000;
            long token1 = 100;
            long token2 = 300;
            long token3 = 301;

            var airdrop1 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .tokenId(token1))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .tokenId(token2))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .tokenId(token3))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender))
                    .persist();

            var uriParams = "?limit=1&sender.id=gte:%s&order=desc".formatted(sender);
            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(receiver);

            // When
            var result = restClient.get().uri(uriParams, receiver).retrieve().body(TokenAirdropsResponse.class);
            var nextParams = "?limit=1&sender.id=gte:1000&sender.id=lte:0.0.1000&order=desc&token.id=lt:0.0.301";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, receiver).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = "?limit=1&sender.id=gte:1000&sender.id=lte:0.0.1000&order=desc&token.id=lt:0.0.300";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, receiver).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = "?limit=1&sender.id=gte:1000&sender.id=lte:0.0.1000&order=desc&token.id=lt:0.0.100";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop1), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, receiver).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void nftFollowDescendingOrderLink() {
            // Given
            long sender = 1000;
            long receiver = 2000;
            long token1 = 100;
            long token2 = 300;
            long token3 = 301;
            long serial1 = 10;
            long serial2 = 20;
            long serial3 = 30;

            var airdrop1 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .serialNumber(serial1)
                            .tokenId(token1))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .serialNumber(serial2)
                            .tokenId(token2))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .serialNumber(serial3)
                            .tokenId(token3))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender))
                    .persist();

            var uriParams = "?limit=1&sender.id=gte:%s&order=desc".formatted(sender);
            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(receiver);

            // When
            var result = restClient.get().uri(uriParams, receiver).retrieve().body(TokenAirdropsResponse.class);
            var nextParams =
                    "?limit=1&sender.id=gte:1000&sender.id=lte:0.0.1000&order=desc&token.id=lte:0.0.301&serialnumber=lt:30";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, receiver).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams =
                    "?limit=1&sender.id=gte:1000&sender.id=lte:0.0.1000&order=desc&token.id=lte:0.0.300&serialnumber=lt:20";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, receiver).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams =
                    "?limit=1&sender.id=gte:1000&sender.id=lte:0.0.1000&order=desc&token.id=lte:0.0.100&serialnumber=lt:10";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop1), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, receiver).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void fungibleFollowAscendingOrderLink() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .tokenId(6L))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(4L)
                            .receiverAccountId(entity.getId())
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(entity.getId());
            var nextParams = "?limit=1&sender.id=gte:0.0.3&token.id=gt:0.0.5";

            // When no primary or secondary parameters are specified
            var uriParams = "?limit=1";
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When primary and secondary fields are specified
            uriParams = "?limit=1&sender.id=gt:2&token.id=gt:4";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When only the secondary field is specified
            uriParams = "?limit=1&token.id=gt:4";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = "?limit=1&sender.id=gte:0.0.3&token.id=gt:0.0.6";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = "?limit=1&sender.id=gte:0.0.4&token.id=gt:0.0.5";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void nftFollowAscendingOrderLink() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(1L)
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(2L)
                            .tokenId(5L))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(4L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(3L)
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(entity.getId());
            var nextParams = "?limit=1&sender.id=gte:0.0.3&token.id=gte:0.0.5&serialnumber=gt:1";

            // When no primary or secondary parameters are specified
            var uriParams = "?limit=1";
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When primary secondary and tertiary fields are specified
            uriParams = "?limit=1&sender.id=gt:2&token.id=gte:4&serialnumber=gt:0";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When only the secondary field is specified
            uriParams = "?limit=1&token.id=gt:4";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = "?limit=1&sender.id=gte:0.0.3&token.id=gte:0.0.5&serialnumber=gt:2";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = "?limit=1&sender.id=gte:0.0.4&token.id=gte:0.0.5&serialnumber=gt:3";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void allParametersFungible() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(2L)
                            .receiverAccountId(entity.getId())
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .tokenId(5L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .tokenId(6L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(4L)
                            .receiverAccountId(entity.getId())
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(entity.getId());
            var uriParams = "?limit=1&sender.id=gte:0.0.1&sender.id=lt:0.0.4&token.id=lte:0.0.5&token.id=gt:0.0.3";

            // When
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams = "?limit=1&sender.id=lt:0.0.4&sender.id=gte:0.0.2&token.id=lte:0.0.5&token.id=gt:0.0.5";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = "?limit=1&sender.id=lt:0.0.4&sender.id=gte:0.0.3&token.id=lte:0.0.5&token.id=gt:0.0.5";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));
        }

        @Test
        void allParametersNft() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(2L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(10L)
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(20L)
                            .tokenId(5L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(7L)
                            .tokenId(6L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(4L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(8L)
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(entity.getId());
            var uriParams = "?limit=1&sender.id=gte:0.0.1&sender.id=lt:0.0.4&token.id=lte:0.0.5&token.id=gt:0.0.3";

            // When
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams =
                    "?limit=1&sender.id=lt:0.0.4&sender.id=gte:0.0.2&token.id=lte:0.0.5&token.id=gte:0.0.5&serialnumber=gt:10";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams =
                    "?limit=1&sender.id=gte:0.0.1&sender.id=lt:0.0.4&token.id=lte:0.0.5&token.id=gt:0.0.3&serialnumber=gt:5";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&sender.id=lt:0.0.4&sender.id=gte:0.0.3&token.id=lte:0.0.5&token.id=gte:0.0.5&serialnumber=gt:20";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));
        }

        @Test
        void allParametersBothTokenTypesFollowAscendingOrder() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(2L)
                            .receiverAccountId(entity.getId())
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .tokenId(5L))
                    .persist();
            var airdropNft = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(2L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(10L)
                            .tokenId(10L))
                    .persist();
            var airdropNft2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(2L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(11L)
                            .tokenId(10L))
                    .persist();
            var airdropNft3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(12L)
                            .tokenId(10L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(2L)
                            .receiverAccountId(entity.getId() + 1)
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(entity.getId());
            var uriParams = "?limit=1&sender.id=gte:0.0.1&sender.id=lt:0.0.4&token.id=lte:0.0.10&token.id=gt:0.0.3";

            // When
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams = "?limit=1&sender.id=lt:0.0.4&sender.id=gte:0.0.2&token.id=lte:0.0.10&token.id=gt:0.0.5";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams =
                    "?limit=1&sender.id=gte:0.0.1&sender.id=lt:0.0.4&token.id=lte:0.0.10&token.id=gt:0.0.3&serialnumber=gt:5";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When following the next link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&sender.id=lt:0.0.4&sender.id=gte:0.0.2&token.id=lte:0.0.10&token.id=gte:0.0.10&serialnumber=gt:10";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&sender.id=lt:0.0.4&sender.id=gte:0.0.2&token.id=lte:0.0.10&token.id=gte:0.0.10&serialnumber=gt:11";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft2), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Serial number is removed from the next link
            nextParams = "?limit=1&sender.id=lt:0.0.4&sender.id=gte:0.0.3&token.id=lte:0.0.10&token.id=gt:0.0.5";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&sender.id=lt:0.0.4&sender.id=gte:0.0.3&token.id=lte:0.0.10&token.id=gte:0.0.10&serialnumber=gt:12";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void allParametersBothTokenTypesFollowDescendingOrder() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(2L)
                            .receiverAccountId(entity.getId())
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .tokenId(5L))
                    .persist();
            var airdropNft = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(2L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(10L)
                            .tokenId(10L))
                    .persist();
            var airdropNft2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(2L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(11L)
                            .tokenId(10L))
                    .persist();
            var airdropNft3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(3L)
                            .receiverAccountId(entity.getId())
                            .serialNumber(12L)
                            .tokenId(10L))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(2L)
                            .receiverAccountId(entity.getId() + 1)
                            .tokenId(5L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(entity.getId());
            var uriParams =
                    "?limit=1&order=desc&sender.id=gte:0.0.1&sender.id=lt:0.0.4&token.id=lte:0.0.10&token.id=gt:0.0.3";

            // When
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams =
                    "?limit=1&order=desc&sender.id=gte:0.0.1&sender.id=lte:0.0.3&token.id=gt:0.0.3&token.id=lte:0.0.10&serialnumber=lt:12";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams =
                    "?limit=1&order=desc&sender.id=gte:0.0.1&sender.id=lt:0.0.4&token.id=lte:0.0.10&token.id=gt:0.0.3&serialnumber=lt:20";
            result = restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // When following the next link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Serial number is removed from the next link
            nextParams =
                    "?limit=1&order=desc&sender.id=gte:0.0.1&sender.id=lte:0.0.3&token.id=gt:0.0.3&token.id=lt:0.0.5";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&order=desc&sender.id=gte:0.0.1&sender.id=lte:0.0.2&token.id=gt:0.0.3&token.id=lte:0.0.10&serialnumber=lt:11";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft2), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&order=desc&sender.id=gte:0.0.1&sender.id=lte:0.0.2&token.id=gt:0.0.3&token.id=lte:0.0.10&serialnumber=lt:10";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            nextParams =
                    "?limit=1&order=desc&sender.id=gte:0.0.1&sender.id=lte:0.0.2&token.id=gt:0.0.3&token.id=lt:0.0.5";
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "0.000000000000000000000000000000000186Fb1b",
                    "0.0.000000000000000000000000000000000186Fb1b",
                    "0x000000000000000000000000000000000186Fb1b",
                    "0.0.AABBCC22",
                    "0.AABBCC22",
                    "AABBCC22"
                })
        void notFound(String accountId) {
            // When
            ThrowingCallable callable =
                    () -> restClient.get().uri("", accountId).retrieve().body(TokenAirdropsResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.NotFound.class, "No account found for the given ID");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "abc",
                    "a.b.c",
                    "0.0.",
                    "0.65537.1001",
                    "0.0.-1001",
                    "9223372036854775807",
                    "0x00000001000000000000000200000000000000034"
                })
        void invalidId(String id) {
            // When
            ThrowingCallable callable =
                    () -> restClient.get().uri("", id).retrieve().body(TokenAirdropsResponse.class);

            // Then
            validateError(
                    callable,
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'id' with value: '" + id + "'");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "abc",
                    "a.b.c",
                    "0.0.",
                    "0.65537.1001",
                    "0.0.-1001",
                    "9223372036854775807",
                    "0x00000001000000000000000200000000000000034"
                })
        void invalidAccountId(String accountId) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?sender.id={accountId}", "0.0.1001", accountId)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(
                    callable,
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'sender.id' with value: '" + accountId + "'");
        }

        @ParameterizedTest
        @CsvSource({
            "101, limit must be less than or equal to 100",
            "-1, limit must be greater than 0",
            "a, Failed to convert 'limit' with value: 'a'"
        })
        void invalidLimit(String limit, String expected) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?limit={limit}", "0.0.1001", limit)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.BadRequest.class, expected);
        }

        @ParameterizedTest
        @CsvSource({
            "ascending, Failed to convert 'order' with value: 'ascending'",
            "dsc, Failed to convert 'order' with value: 'dsc'",
            "invalid, Failed to convert 'order' with value: 'invalid'"
        })
        void invalidOrder(String order, String expected) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?order={order}", "0.0.1001", order)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.BadRequest.class, expected);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "abc",
                    "a.b.c",
                    "0.0.",
                    "0.65537.1001",
                    "0.0.-1001",
                    "9223372036854775807",
                    "0x00000001000000000000000200000000000000034"
                })
        void invalidTokenId(String tokenId) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?token.id={tokenId}", "0.0.1001", tokenId)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(
                    callable,
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'token.id' with value: '" + tokenId + "'");
        }
    }
}
