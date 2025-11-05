// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static org.hiero.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;

import com.google.common.io.BaseEncoding;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.rest.model.TokenAirdropsResponse;
import org.hiero.mirror.restjava.mapper.TokenAirdropMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
final class TokenAirdropsControllerTest extends ControllerTest {

    private final TokenAirdropMapper mapper;

    private TokenAirdropsResponse getExpectedResponse(List<TokenAirdrop> tokenAirdrops, String next) {
        return new TokenAirdropsResponse().airdrops(mapper.map(tokenAirdrops)).links(new Links().next(next));
    }

    @DisplayName("/api/v1/accounts/{id}/airdrops/outstanding")
    @Nested
    final class OutstandingTokenAirdropsEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "accounts/{id}/airdrops/outstanding";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            var sender = domainBuilder.entityNum(1000);
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(e -> e.senderAccountId(sender.getId()))
                    .persist();
            return uriSpec.uri("", sender.getNum());
        }

        @TestFactory
        Stream<DynamicTest> entityId() {
            final var entityId = domainBuilder.entityNum(1000);
            final var inputs = List.of(
                    entityId.toString(),
                    String.format("%d.%d", entityId.getRealm(), entityId.getNum()),
                    Long.toString(entityId.getNum()));
            final var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entityId.getId()))
                    .persist();
            final ThrowingConsumer<String> executor = input -> {
                // When
                final var response = restClient.get().uri("", input).retrieve().toEntity(TokenAirdropsResponse.class);

                // Then
                assertThat(response.getBody())
                        .extracting(
                                TokenAirdropsResponse::getAirdrops, InstanceOfAssertFactories.list(TokenAirdrop.class))
                        .first()
                        .isEqualTo(mapper.map(tokenAirdrop));
                // Based on application.yml response headers configuration
                assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
                assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=1");
            };

            return DynamicTest.stream(inputs.iterator(), Function.identity(), executor);
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
            var receiver = domainBuilder.entityNum(2000);
            var sender = domainBuilder.entityNum(1000);
            var token1 = domainBuilder.entityNum(100);
            var token2 = domainBuilder.entityNum(300);
            var token3 = domainBuilder.entityNum(301);

            var airdrop1 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token2.getId()))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token3.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.receiverAccountId(receiver.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.receiverAccountId(receiver.getId()))
                    .persist();

            var uriParams = "?limit=1&receiver.id=gte:%s&order=desc".formatted(receiver.getNum());
            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(sender.getNum());
            var nextBase = "?limit=1&receiver.id=gte:%d&receiver.id=lte:%s&order=desc&token.id=lt:%s";

            // When
            var result =
                    restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // The first receiver id is '2000' instead of 0.0.2000 because the link creation does not alter the original
            // value sent in the request
            // The second receiver id is added by the link generator and has shard.realm.num format
            var nextParams = nextBase.formatted(receiver.getNum(), receiver, token3);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = nextBase.formatted(receiver.getNum(), receiver, token2);
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = nextBase.formatted(receiver.getNum(), receiver, token1);
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop1), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, sender).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void nftFollowDescendingOrderLink() {
            // Given
            var sender = domainBuilder.entityNum(1000);
            var receiver = domainBuilder.entityNum(2000);
            var token1 = domainBuilder.entityNum(100);
            var token2 = domainBuilder.entityNum(300);
            var token3 = domainBuilder.entityNum(301);
            var serial1 = 10;
            var serial2 = 20;
            var serial3 = 30;

            var airdrop1 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial1)
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial2)
                            .tokenId(token2.getId()))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial3)
                            .tokenId(token3.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.receiverAccountId(receiver.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.receiverAccountId(receiver.getId()))
                    .persist();

            var uriParams = "?limit=1&receiver.id=gte:%s&order=desc".formatted(receiver.getNum());
            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(sender.getNum());
            var nextBase =
                    "?limit=1&receiver.id=gte:%d&receiver.id=lte:%s&order=desc&token.id=lte:%s&serialnumber=lt:%d";

            // When
            var result =
                    restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams = nextBase.formatted(receiver.getNum(), receiver, token3, serial3);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = nextBase.formatted(receiver.getNum(), receiver, token2, serial2);
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = nextBase.formatted(receiver.getNum(), receiver, token1, serial1);
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop1), baseLink + nextParams));

            // When follow link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void fungibleFollowAscendingOrderLink() {
            // Given
            var receiver1 = domainBuilder.entityNum(3);
            var receiver2 = domainBuilder.entityNum(4);
            var sender = domainBuilder.entityNum(1000);
            var token1 = domainBuilder.entityNum(5);
            var token2 = domainBuilder.entityNum(6);

            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .tokenId(token2.getId()))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver2.getId())
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(sender.getNum());
            var nextBase = "?limit=1&receiver.id=gte:%s&token.id=gt:%s";
            var nextParams = nextBase.formatted(receiver1, token1);

            // When no primary or secondary parameters are specified
            var uriParams = "?limit=1";
            var result =
                    restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When primary secondary and tertiary fields are specified
            uriParams = "?limit=1&receiver.id=gt:2&token.id=gte:4&serialnumber=gt:0";
            result = restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When only the secondary field is specified
            uriParams = "?limit=1&token.id=gt:%d".formatted(token1.getNum() - 1);
            result = restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When follow link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(receiver1, token2);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(receiver2, token1);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void nftFollowAscendingOrderLink() {
            // Given
            var receiver1 = domainBuilder.entityNum(3);
            var receiver2 = domainBuilder.entityNum(4);
            var sender = domainBuilder.entityNum(1000);
            var token1 = domainBuilder.entityNum(5);
            var serial1 = 1;
            var serial2 = 2;
            var serial3 = 3;

            // Given
            var airdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .serialNumber(serial1)
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .serialNumber(serial2)
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver2.getId())
                            .serialNumber(serial3)
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(sender.getNum());
            var nextBase = "?limit=1&receiver.id=gte:%s&token.id=gte:%s&serialnumber=gt:%d";
            var nextParams = nextBase.formatted(receiver1, token1, serial1);

            // When no primary or secondary parameters are specified
            var uriParams = "?limit=1";
            var result =
                    restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When primary secondary and tertiary fields are specified
            uriParams = "?limit=1&receiver.id=gt:%d&token.id=gte:%d&serialnumber=gt:%d"
                    .formatted(receiver1.getNum() - 1, token1.getNum() - 1, serial1 - 1);
            result = restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When only the secondary field is specified
            uriParams = "?limit=1&token.id=gt:%d".formatted(token1.getNum() - 1);
            result = restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When follow link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(receiver1, token1, serial2);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(receiver2, token1, serial3);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void allParametersFungible() {
            // Given
            var receiver = domainBuilder.entityNum(1);
            var receiver1 = domainBuilder.entityNum(2);
            var receiver2 = domainBuilder.entityNum(3);
            var receiver3 = domainBuilder.entityNum(4);
            var sender = domainBuilder.entityNum(1000);
            var token1 = domainBuilder.entityNum(5);
            var token2 = domainBuilder.entityNum(6);

            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver2.getId())
                            .tokenId(token1.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver2.getId())
                            .tokenId(token2.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver3.getId())
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(sender.getNum());
            var uriParams = "?limit=1&receiver.id=gte:%s&receiver.id=lt:%s&token.id=lte:%s&token.id=gt:%s"
                    .formatted(receiver, receiver3, token1, receiver2);
            var nextBase = "?limit=1&receiver.id=lt:%s&receiver.id=gte:%s&token.id=lte:%s&token.id=gt:%s";

            // When
            var result =
                    restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams = nextBase.formatted(receiver3, receiver1, token1, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(receiver3, receiver2, token1, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));
        }

        @Test
        void allParametersNft() {
            // Given
            var receiver1 = domainBuilder.entityNum(2000);
            var receiver2 = domainBuilder.entityNum(2001);
            var receiver3 = domainBuilder.entityNum(3000);
            var receiver4 = domainBuilder.entityNum(3001);
            var sender = domainBuilder.entityNum(1000);
            var token1 = domainBuilder.entityNum(4000);
            var token2 = domainBuilder.entityNum(4001);
            var token3 = domainBuilder.entityNum(5000);
            var serial1 = 5;
            var serial2 = 6;
            var serial3 = 100;

            var airdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .tokenId(token1.getId())
                            .serialNumber(serial1))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver2.getId())
                            .tokenId(token2.getId())
                            .serialNumber(serial3))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver3.getId())
                            .tokenId(token3.getId())
                            .serialNumber(serial2))
                    .persist();
            var serialOutsideRangeAirdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .tokenId(token2.getId())
                            .serialNumber(serial1))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver4.getId())
                            .tokenId(token2.getId()))
                    .persist();

            var uriParams =
                    "?receiver.id=gte:%s&receiver.id=lte:%s&token.id=gte:%s&token.id=lte:%s&serialnumber=gte:%d&serialnumber=lte:%d"
                            .formatted(receiver1, receiver3, token1, token3, serial1, serial3);
            // When
            var result =
                    restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result)
                    .isEqualTo(
                            getExpectedResponse(List.of(airdrop, serialOutsideRangeAirdrop, airdrop2, airdrop3), null));
        }

        @Test
        void allParametersNftFollowLink() {
            // Given
            var receiver = domainBuilder.entityNum(1);
            var receiver1 = domainBuilder.entityNum(2);
            var receiver2 = domainBuilder.entityNum(3);
            var receiver3 = domainBuilder.entityNum(4);
            var sender = domainBuilder.entityNum(1000);
            var token1 = domainBuilder.entityNum(5);
            var token2 = domainBuilder.entityNum(6);
            var serial1 = 7;
            var serial2 = 8;
            var serial3 = 10;
            var serial4 = 20;

            var airdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .serialNumber(serial3)
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver2.getId())
                            .serialNumber(serial4)
                            .tokenId(token1.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver2.getId())
                            .serialNumber(serial1)
                            .tokenId(token2.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver3.getId())
                            .serialNumber(serial2)
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(sender.getNum());
            var uriParams = "?limit=1&receiver.id=gte:%s&receiver.id=lt:%s&token.id=lte:%s&token.id=gt:%s"
                    .formatted(receiver, receiver3, token1, receiver2);
            var nextBaseSerial =
                    "?limit=1&receiver.id=lt:%s&receiver.id=gte:%s&token.id=lte:%s&token.id=gte:%s&serialnumber=gt:%d";

            // When
            var result =
                    restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams = nextBaseSerial.formatted(receiver3, receiver1, token1, token1, serial3);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams += "&serialnumber=gt:%d".formatted(serial1 - 1);
            result = restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBaseSerial.formatted(receiver3, receiver2, token1, token1, serial4);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));
        }

        @Test
        void allParametersBothTokenTypesFollowAscendingOrder() {
            // Given
            var receiver = domainBuilder.entityNum(1);
            var receiver1 = domainBuilder.entityNum(2);
            var receiver2 = domainBuilder.entityNum(3);
            var receiver3 = domainBuilder.entityNum(4);
            var sender = domainBuilder.entityNum(1000);
            var token1 = domainBuilder.entityNum(5);
            var token2 = domainBuilder.entityNum(10);
            var serial1 = 10;
            var serial2 = 11;
            var serial3 = 12;

            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver2.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdropNft = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .serialNumber(serial1)
                            .tokenId(token2.getId()))
                    .persist();
            var airdropNft2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .serialNumber(serial2)
                            .tokenId(token2.getId()))
                    .persist();
            var airdropNft3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver2.getId())
                            .serialNumber(serial3)
                            .tokenId(token2.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId() + 1)
                            .receiverAccountId(receiver1.getId())
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(sender.getNum());
            var uriParams = "?limit=1&receiver.id=gte:%s&receiver.id=lt:%s&token.id=lte:%s&token.id=gt:%s"
                    .formatted(receiver, receiver3, token2, receiver2);
            var nextBase = "?limit=1&receiver.id=lt:%s&receiver.id=gte:%s&token.id=lte:%s&token.id=gt:%s";
            var nextBaseSerial =
                    "?limit=1&receiver.id=lt:%s&receiver.id=gte:%s&token.id=lte:%s&token.id=gte:%s&serialnumber=gt:%d";

            // When
            var result =
                    restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams = nextBase.formatted(receiver3, receiver1, token2, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams += "&serialnumber=gt:%d".formatted(serial1 - 1);
            result = restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When following the next link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBaseSerial.formatted(receiver3, receiver1, token2, token2, serial1);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft), baseLink + nextParams));

            // When
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBaseSerial.formatted(receiver3, receiver1, token2, token2, serial2);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft2), baseLink + nextParams));

            // When
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Serial number is removed from the next link
            nextParams = nextBase.formatted(receiver3, receiver2, token2, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBaseSerial.formatted(receiver3, receiver2, token2, token2, serial3);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // When
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void allParametersBothTokenTypesFollowDescendingOrder() {
            // Given
            var receiver = domainBuilder.entityNum(1);
            var receiver1 = domainBuilder.entityNum(2);
            var receiver2 = domainBuilder.entityNum(3);
            var receiver3 = domainBuilder.entityNum(4);
            var sender = domainBuilder.entityNum(1000);
            var token1 = domainBuilder.entityNum(5);
            var token2 = domainBuilder.entityNum(10);
            var serial1 = 10;
            var serial2 = 11;
            var serial3 = 12;

            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver2.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdropNft = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .serialNumber(serial1)
                            .tokenId(token2.getId()))
                    .persist();
            var airdropNft2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver1.getId())
                            .serialNumber(serial2)
                            .tokenId(token2.getId()))
                    .persist();
            var airdropNft3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver2.getId())
                            .serialNumber(serial3)
                            .tokenId(token2.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId() + 1)
                            .receiverAccountId(receiver1.getId())
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(sender.getNum());
            var uriParams = "?limit=1&order=desc&receiver.id=gte:%s&receiver.id=lt:%s&token.id=lte:%s&token.id=gt:%s"
                    .formatted(receiver, receiver3, token2, receiver2);
            var nextBase = "?limit=1&order=desc&receiver.id=gte:%s&receiver.id=lte:%s&token.id=gt:%s&token.id=lt:%s";
            var nextBaseSerial =
                    "?limit=1&order=desc&receiver.id=gte:%s&receiver.id=lte:%s&token.id=gt:%s&token.id=lte:%s&serialnumber=lt:%d";

            // When
            var result =
                    restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            var nextParams = nextBaseSerial.formatted(receiver, receiver2, receiver2, token2, serial3);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams =
                    "?limit=1&order=desc&receiver.id=gte:%s&receiver.id=lt:%s&token.id=lte:%s&token.id=gt:%s&serialnumber=lt:%d"
                            .formatted(receiver, receiver3, token2, receiver2, serial3 + 1);
            result = restClient.get().uri(uriParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // When following the next link
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Serial number is removed from the next link
            nextParams = nextBase.formatted(receiver, receiver2, receiver2, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBaseSerial.formatted(receiver, receiver1, receiver2, token2, serial2);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft2), baseLink + nextParams));

            // When
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBaseSerial.formatted(receiver, receiver1, receiver2, token2, serial1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft), baseLink + nextParams));

            // When
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(receiver, receiver1, receiver2, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result =
                    restClient.get().uri(nextParams, sender.getNum()).retrieve().body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "0." + EVM_ADDRESS,
                    "0.0." + EVM_ADDRESS,
                    "0x" + EVM_ADDRESS,
                    "0.0." + ALIAS,
                    "0." + ALIAS,
                    ALIAS
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
            validateError(callable, HttpClientErrorException.BadRequest.class, "Failed to convert 'id'");
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
            validateError(callable, HttpClientErrorException.BadRequest.class, "Failed to convert 'receiver.id'");
        }

        @ParameterizedTest
        @CsvSource({
            "101, limit must be less than or equal to 100",
            "-1, limit must be greater than 0",
            "a, Failed to convert 'limit'"
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
            "ascending, Failed to convert 'order'",
            "dsc, Failed to convert 'order'",
            "invalid, Failed to convert 'order'"
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
            validateError(callable, HttpClientErrorException.BadRequest.class, "Failed to convert 'token.id'");
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
            var receiverId = domainBuilder.entityNum(1000);
            var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(t -> t.receiverAccountId(receiverId.getId()))
                    .persist();

            return uriSpec.uri("", receiverId.getNum());
        }

        @TestFactory
        Stream<DynamicTest> entityId() {
            final var entityId = domainBuilder.entityNum(1000);
            final var inputs = List.of(
                    entityId.toString(),
                    String.format("%d.%d", entityId.getRealm(), entityId.getNum()),
                    Long.toString(entityId.getNum()));
            final var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.receiverAccountId(entityId.getId()))
                    .persist();
            final ThrowingConsumer<String> executor = input -> {
                // When
                final var response = restClient.get().uri("", input).retrieve().toEntity(TokenAirdropsResponse.class);

                // Then
                assertThat(response.getBody())
                        .extracting(
                                TokenAirdropsResponse::getAirdrops, InstanceOfAssertFactories.list(TokenAirdrop.class))
                        .first()
                        .isEqualTo(mapper.map(tokenAirdrop));
                // Based on application.yml response headers configuration
                assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
                assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=1");
            };

            return DynamicTest.stream(inputs.iterator(), Function.identity(), executor);
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
            var sender = domainBuilder.entityNum(1000);
            var receiver = domainBuilder.entityNum(2000);
            var token1 = domainBuilder.entityNum(100);
            var token2 = domainBuilder.entityNum(300);
            var token3 = domainBuilder.entityNum(301);

            var airdrop1 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token2.getId()))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token3.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId()))
                    .persist();

            var uriParams = "?limit=1&sender.id=gte:%s&order=desc".formatted(sender.getNum());
            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(receiver.getNum());
            var nextBase = "?limit=1&sender.id=gte:%d&sender.id=lte:%s&order=desc&token.id=lt:%s";

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            var nextParams = nextBase.formatted(sender.getNum(), sender, token3);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            nextParams = nextBase.formatted(sender.getNum(), sender, token2);
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            nextParams = nextBase.formatted(sender.getNum(), sender, token1);
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop1), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, receiver).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void nftFollowDescendingOrderLink() {
            // Given
            var sender = domainBuilder.entityNum(1000);
            var receiver = domainBuilder.entityNum(2000);
            var token1 = domainBuilder.entityNum(100);
            var token2 = domainBuilder.entityNum(300);
            var token3 = domainBuilder.entityNum(301);
            var serial1 = 10;
            var serial2 = 20;
            var serial3 = 30;

            var airdrop1 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial1)
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial2)
                            .tokenId(token2.getId()))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial3)
                            .tokenId(token3.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId()))
                    .persist();

            var uriParams = "?limit=1&sender.id=gte:%s&order=desc".formatted(sender.getNum());
            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(receiver.getNum());
            var nextBase = "?limit=1&sender.id=gte:%d&sender.id=lte:%s&order=desc&token.id=lte:%s&serialnumber=lt:%d";

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            var nextParams = nextBase.formatted(sender.getNum(), sender, token3, serial3);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            nextParams = nextBase.formatted(sender.getNum(), sender, token2, serial2);
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            nextParams = nextBase.formatted(sender.getNum(), sender, token1, serial1);
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop1), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void fungibleFollowAscendingOrderLink() {
            // Given
            var receiver = domainBuilder.entityNum(2000);
            var sender = domainBuilder.entityNum(3);
            var sender1 = domainBuilder.entityNum(4);
            var token1 = domainBuilder.entityNum(5);
            var token2 = domainBuilder.entityNum(6);

            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token2.getId()))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(receiver.getNum());
            var nextBase = "?limit=1&sender.id=gte:%s&token.id=gt:%s";

            // When no primary or secondary parameters are specified
            var uriParams = "?limit=1";
            var result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            var nextParams = nextBase.formatted(sender, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When primary and secondary fields are specified
            uriParams = "?limit=1&sender.id=gt:%d&token.id=gt:%d".formatted(sender.getNum() - 1, token1.getNum() - 1);
            result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When only the secondary field is specified
            uriParams = "?limit=1&token.id=gt:%d".formatted(token1.getNum() - 1);
            result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(sender, token2);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(sender1, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void nftFollowAscendingOrderLink() {
            // Given
            var receiver = domainBuilder.entityNum(2000);
            var sender = domainBuilder.entityNum(3);
            var sender1 = domainBuilder.entityNum(4);
            var token1 = domainBuilder.entityNum(5);
            var token2 = domainBuilder.entityNum(6);
            var serial1 = 1L;
            var serial2 = 2L;
            var serial3 = 3L;

            var entity = domainBuilder
                    .entity()
                    .customize(e -> e.id(receiver.getId()))
                    .persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial1)
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender.getId())
                            .receiverAccountId(entity.getId())
                            .serialNumber(2L)
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(entity.getId())
                            .serialNumber(3L)
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(receiver.getNum());
            var nextBase = "?limit=1&sender.id=gte:%s&token.id=gte:%s&serialnumber=gt:%d";
            var nextParams = nextBase.formatted(sender, token1, serial1);

            // When no primary or secondary parameters are specified
            var uriParams = "?limit=1";
            var result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When primary secondary and tertiary fields are specified
            uriParams = "?limit=1&sender.id=gt:%d&token.id=gte:%d&serialnumber=gt:%d"
                    .formatted(sender.getNum() - 1, token1.getNum() - 1, serial1 - 1);
            result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When only the secondary field is specified
            uriParams = "?limit=1&token.id=gt:%d".formatted(token1.getNum() - 1);
            result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(sender, token1, serial2);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(sender1, token1, serial3);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void allParametersFungible() {
            // Given
            var sender = domainBuilder.entityNum(1);
            var sender1 = domainBuilder.entityNum(2);
            var sender2 = domainBuilder.entityNum(3);
            var sender3 = domainBuilder.entityNum(4);
            var token1 = domainBuilder.entityNum(5);
            var token2 = domainBuilder.entityNum(6);
            var receiver = domainBuilder.entityNum(1000);

            var entity = domainBuilder
                    .entity()
                    .customize(e -> e.id(receiver.getId()))
                    .persist();

            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender2.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token1.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender2.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token2.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender3.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(receiver.getNum());
            var uriParams = "?limit=1&sender.id=gte:%s&sender.id=lt:%s&token.id=lte:%s&token.id=gt:%s"
                    .formatted(sender, sender3, token1, sender2);
            var nextBase = "?limit=1&sender.id=lt:%s&sender.id=gte:%s&token.id=lte:%s&token.id=gt:%s";

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            var nextParams = nextBase.formatted(sender3, sender1, token1, token1);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(sender3, sender2, token1, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));
        }

        @Test
        void allParametersNft() {
            // Given
            var sender = domainBuilder.entityNum(1);
            var sender1 = domainBuilder.entityNum(2);
            var sender2 = domainBuilder.entityNum(3);
            var sender3 = domainBuilder.entityNum(4);
            var token1 = domainBuilder.entityNum(5);
            var token2 = domainBuilder.entityNum(6);
            var receiver = domainBuilder.entityNum(1000);
            var serial1 = 10L;
            var serial2 = 20L;
            var serial3 = 7L;
            var serial4 = 8L;

            var entity = domainBuilder
                    .entity()
                    .customize(e -> e.id(receiver.getId()))
                    .persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial1)
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender2.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial2)
                            .tokenId(token1.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender2.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial3)
                            .tokenId(token2.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender3.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial4)
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(receiver.getNum());
            var uriParams = "?limit=1&sender.id=gte:%s&sender.id=lt:%s&token.id=lte:%s&token.id=gt:%s"
                    .formatted(sender, sender3, token1, sender2);
            var nextBase =
                    "?limit=1&sender.id=lt:%s&sender.id=gte:%s&token.id=lte:%s&token.id=gte:%s&serialnumber=gt:%d";

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            var nextParams = nextBase.formatted(sender3, sender1, token1, token1, serial1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams += "&serialnumber=gt:%d".formatted(serial1);
            result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(sender3, sender2, token1, token1, serial2);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));
        }

        @Test
        void allParametersBothTokenTypesFollowAscendingOrder() {
            // Given
            var sender = domainBuilder.entityNum(1);
            var sender1 = domainBuilder.entityNum(2);
            var sender2 = domainBuilder.entityNum(3);
            var sender3 = domainBuilder.entityNum(4);
            var token1 = domainBuilder.entityNum(5);
            var token2 = domainBuilder.entityNum(10);
            var receiver = domainBuilder.entityNum(1000);
            var serial1 = 10L;
            var serial2 = 11L;
            var serial3 = 12L;

            var entity = domainBuilder
                    .entity()
                    .customize(e -> e.id(receiver.getId()))
                    .persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender2.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdropNft = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial1)
                            .tokenId(token2.getId()))
                    .persist();
            var airdropNft2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial2)
                            .tokenId(token2.getId()))
                    .persist();
            var airdropNft3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender2.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial3)
                            .tokenId(token2.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(receiver.getId() + 1)
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(receiver.getNum());
            var uriParams = "?limit=1&sender.id=gte:%s&sender.id=lt:%s&token.id=lte:%s&token.id=gt:%s"
                    .formatted(sender, sender3, token2, sender2);
            var nextBase = "?limit=1&sender.id=lt:%s&sender.id=gte:%s&token.id=lte:%s&token.id=gt:%s";
            var nextBaseSerial =
                    "?limit=1&sender.id=lt:%s&sender.id=gte:%s&token.id=lte:%s&token.id=gte:%s&serialnumber=gt:%d";

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            var nextParams = nextBase.formatted(sender3, sender1, token2, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams += "&serialnumber=gt:5";
            result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When following the next link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBaseSerial.formatted(sender3, sender1, token2, token2, serial1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft), baseLink + nextParams));

            // When
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBaseSerial.formatted(sender3, sender1, token2, token2, serial2);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft2), baseLink + nextParams));

            // When
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            // Serial number is removed from the next link
            nextParams = nextBase.formatted(sender3, sender2, token2, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBaseSerial.formatted(sender3, sender2, token2, token2, serial3);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // When
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void allParametersBothTokenTypesFollowDescendingOrder() {
            // Given
            var sender = domainBuilder.entityNum(1);
            var sender1 = domainBuilder.entityNum(2);
            var sender2 = domainBuilder.entityNum(3);
            var sender3 = domainBuilder.entityNum(4);
            var token1 = domainBuilder.entityNum(5);
            var token2 = domainBuilder.entityNum(10);
            var receiver = domainBuilder.entityNum(1000);
            var serial1 = 10L;
            var serial2 = 11L;
            var serial3 = 12L;

            var entity = domainBuilder
                    .entity()
                    .customize(e -> e.id(receiver.getId()))
                    .persist();

            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender2.getId())
                            .receiverAccountId(receiver.getId())
                            .tokenId(token1.getId()))
                    .persist();
            var airdropNft = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial1)
                            .tokenId(token2.getId()))
                    .persist();
            var airdropNft2 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial2)
                            .tokenId(token2.getId()))
                    .persist();
            var airdropNft3 = domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.senderAccountId(sender2.getId())
                            .receiverAccountId(receiver.getId())
                            .serialNumber(serial3)
                            .tokenId(token2.getId()))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender1.getId())
                            .receiverAccountId(receiver.getId() + 1)
                            .tokenId(token1.getId()))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/pending".formatted(receiver.getNum());
            var uriParams = "?limit=1&order=desc&sender.id=gte:%s&sender.id=lt:%s&token.id=lte:%s&token.id=gt:%s"
                    .formatted(sender, sender3, token2, sender2);
            var nextBase = "?limit=1&order=desc&sender.id=gte:%s&sender.id=lte:%s&token.id=gt:%s&token.id=lt:%s";
            var nextBaseSerial =
                    "?limit=1&order=desc&sender.id=gte:%s&sender.id=lte:%s&token.id=gt:%s&token.id=lte:%s&serialnumber=lt:%d";

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            var nextParams = nextBaseSerial.formatted(sender, sender2, sender2, token2, serial3);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // Add serial number to the initial request
            uriParams += "&serialnumber=lt:20";
            result = restClient
                    .get()
                    .uri(uriParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft3), baseLink + nextParams));

            // When following the next link
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            // Serial number is removed from the next link
            nextParams = nextBase.formatted(sender, sender2, sender2, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBaseSerial.formatted(sender, sender1, sender2, token2, serial2);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft2), baseLink + nextParams));

            // When
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBaseSerial.formatted(sender, sender1, sender2, token2, serial1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdropNft), baseLink + nextParams));

            // When
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            nextParams = nextBase.formatted(sender, sender1, sender2, token1);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When
            result = restClient
                    .get()
                    .uri(nextParams, receiver.getNum())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "0." + EVM_ADDRESS,
                    "0.0." + EVM_ADDRESS,
                    "0x" + EVM_ADDRESS,
                    "0.0." + ALIAS,
                    "0." + ALIAS,
                    ALIAS
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
            validateError(callable, HttpClientErrorException.BadRequest.class, "Failed to convert 'id'");
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
            validateError(callable, HttpClientErrorException.BadRequest.class, "Failed to convert 'sender.id'");
        }

        @ParameterizedTest
        @CsvSource({
            "101, limit must be less than or equal to 100",
            "-1, limit must be greater than 0",
            "a, Failed to convert 'limit'"
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
            "ascending, Failed to convert 'order'",
            "dsc, Failed to convert 'order'",
            "invalid, Failed to convert 'order'"
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
            validateError(callable, HttpClientErrorException.BadRequest.class, "Failed to convert 'token.id'");
        }
    }
}
