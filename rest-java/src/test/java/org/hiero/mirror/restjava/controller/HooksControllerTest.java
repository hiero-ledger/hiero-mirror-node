// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.common.Constants.MAX_REPEATED_QUERY_PARAMETERS;

import com.google.common.io.BaseEncoding;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.rest.model.HooksResponse;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.restjava.mapper.HookMapper;
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
final class HooksControllerTest extends ControllerTest {

    private final HookMapper hookMapper;

    @DisplayName("/api/v1/accounts/{accountId}/hooks")
    @Nested
    final class HooksEndpointTest extends EndpointTest {

        private static final long ACCOUNT_ID = 1234L;

        @Override
        protected String getUrl() {
            return "accounts/{account_id}/hooks";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            persistHook(ACCOUNT_ID);

            return uriSpec.uri("", ACCOUNT_ID);
        }

        @ParameterizedTest
        @CsvSource({
            // entity ID formats
            "1234",
            "0.1234",
            "0.0.1234",
            // alias formats
            ALIAS,
            "0." + ALIAS,
            "0.0." + ALIAS,
            // evm formats
            EVM_ADDRESS,
            "0." + EVM_ADDRESS,
            "0.0." + EVM_ADDRESS,
            "0x" + EVM_ADDRESS,
        })
        void success(String address) {
            // given
            persistAccount(ACCOUNT_ID, ALIAS, EVM_ADDRESS);

            final var hook1 = persistHook(ACCOUNT_ID);
            final var hook2 = persistHook(ACCOUNT_ID);

            final var expectedHooks = hookMapper.map(List.of(hook2, hook1));
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);
            expectedResponse.setLinks(new Links());

            // when
            final var actual = restClient.get().uri("", address).retrieve().body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @Test
        void noHooksFound() {
            // given
            persistHook(ACCOUNT_ID + 1);
            final var expectedHooks = hookMapper.map(Collections.emptyList());
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);
            expectedResponse.setLinks(new Links());

            // when
            final var actual = restClient.get().uri("", ACCOUNT_ID).retrieve().body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @ParameterizedTest
        @ValueSource(strings = {"asc", "desc"})
        void limitAndNextLink(String order) {
            // given
            final int limit = 2;

            final var hook1 = persistHook(ACCOUNT_ID);
            final var hook2 = persistHook(ACCOUNT_ID);
            final var hook3 = persistHook(ACCOUNT_ID);

            final var expectedHookEntities = order.equals("asc") ? List.of(hook1, hook2) : List.of(hook3, hook2);

            final var expectedHooks = hookMapper.map(expectedHookEntities);
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);

            final var lastHook = expectedHookEntities.getLast();
            final var operator = order.equals("asc") ? "gt" : "lt";

            final var nextLink = String.format(
                    "/api/v1/accounts/%d/hooks?limit=%d&order=%s&hook.id=%s:%d",
                    ACCOUNT_ID, limit, order, operator, lastHook.getHookId());

            final var links = new Links();
            links.setNext(nextLink);
            expectedResponse.setLinks(links);

            // when
            final var actual = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("limit", limit)
                            .queryParam("order", order)
                            .build(Map.of("account_id", ACCOUNT_ID)))
                    .retrieve()
                    .body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
            assertThat(actual.getHooks()).hasSize(limit);
        }

        @ParameterizedTest
        @CsvSource(
                delimiter = '|',
                nullValues = "NULL",
                value = {
                    // --- SCENARIO 1: 'eq' or no-op (defaults to eq) ---
                    "'?hook.id=eq:{1}' | 1", // Single eq
                    "'?hook.id={1}' | 1", // Single no-op
                    "'?hook.id=eq:{1}&hook.id=eq:{2}' | 2,1", // Multiple eq
                    "'?hook.id={1}&hook.id={2}' | 2,1", // Multiple no-op
                    "'?hook.id=eq:{1}&hook.id={3}' | 3,1", // Multiple eq
                    "'?hook.id=eq:{1}&hook.id={1}' | 1", // Duplicate eq
                    "'?hook.id=eq:{0}&hook.id={1}&hook.id=eq:{2}' | 2,1,0", // Mixed eq and
                    // --- SCENARIO 2: Range only ---
                    "'?hook.id=lt:{2}' | 1,0", // Single lt
                    "'?hook.id=lte:{2}'| 2,1,0", // Single lte
                    "'?hook.id=gt:{1}' | 3,2", // Single gt
                    "'?hook.id=gte:{1}'| 3,2,1", // Single gte
                    "'?hook.id=lt:{2}&hook.id=lt:{3}' | 1,0", // Multiple lt
                    "'?hook.id=lte:{2}&hook.id=lt:{2}' | 1,0", // Multiple lt/lte
                    "'?hook.id=gt:{1}&hook.id=gt:{0}' | 3,2", // Multiple gt
                    "'?hook.id=gte:{1}&hook.id=gt:{1}' | 3,2", // Multiple gt/gte
                    "'?hook.id=gt:{0}&hook.id=lt:{3}' | 2,1", // Range (gt + lt)
                    "'?hook.id=gte:{1}&hook.id=lte:{2}' | 2,1", // Range (gte + lte)
                    "'?hook.id=gt:{3}&hook.id=lt:{0}' | NULL", // Range (gt + lt) no overlap
                    "'?hook.id=gt:{2}&hook.id=lt:{2}' | NULL", // Range (gt + lt) no overlap edge
                    // --- SCENARIO 3: 'eq'/'no-op' + Range (No Overlap) ---
                    "'?hook.id=eq:{0}&hook.id=gt:{1}' | NULL", // eq + gt no overlap
                    "'?hook.id={3}&hook.id=lt:{2}' | NULL", // no-op + lt no overlap
                    "'?hook.id=eq:{1}&hook.id=gt:{1}' | NULL", // eq + gt edge no overlap
                    "'?hook.id={1}&hook.id=lt:{1}' | NULL", // no-op + lt edge no overlap
                    // --- SCENARIO 4: 'eq'/'no-op' + Range (Overlap) ---
                    "'?hook.id=eq:{1}&hook.id=gt:{0}' | 1", // eq + gt overlap
                    "'?hook.id={1}&hook.id=gte:{1}' | 1", // no-op + gte edge overlap
                    "'?hook.id=eq:{2}&hook.id=lt:{3}' | 2", // eq + lt overlap
                    "'?hook.id={2}&hook.id=lte:{2}' | 2", // no-op + lte edge overlap
                    "'?hook.id=eq:{1}&hook.id={2}&hook.id=gt:{0}' | 2,1", // Multi eq/no-op + gt (all overlap)
                    "'?hook.id=eq:{1}&hook.id={2}&hook.id=gt:{1}' | 2", // Multi eq/no-op + gt (partial overlap)
                    "'?hook.id=eq:{0}&hook.id={1}&hook.id={2}&hook.id=gt:{0}&hook.id=lt:{2}' | 1"
                })
        void hookIdBounds(String parameters, String expectedIndices) {
            // given
            final var hooks = new ArrayList<Hook>();
            for (int i = 0; i < 4; i++) {
                hooks.add(persistHook(ACCOUNT_ID));
            }

            final List<Hook> expectedHookList;
            if (expectedIndices == null) {
                expectedHookList = Collections.emptyList();
            } else {
                expectedHookList = Arrays.stream(expectedIndices.split(","))
                        .map(String::trim)
                        .mapToInt(Integer::parseInt)
                        .mapToObj(hooks::get)
                        .sorted(Comparator.comparing(Hook::getHookId).reversed())
                        .collect(Collectors.toList());
            }

            final var expectedHooks = hookMapper.map(expectedHookList);
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);
            expectedResponse.setLinks(new Links());

            // when
            final var formattedParams = MessageFormat.format(
                    parameters,
                    hooks.get(0).getHookId(),
                    hooks.get(1).getHookId(),
                    hooks.get(2).getHookId(),
                    hooks.get(3).getHookId());

            final var actual =
                    restClient.get().uri(formattedParams, ACCOUNT_ID).retrieve().body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @Test
        void invalidHookIdTooManyParameters() {
            // given
            final var params = IntStream.range(0, MAX_REPEATED_QUERY_PARAMETERS + 1)
                    .mapToObj(i -> "hook.id=eq:" + i)
                    .collect(Collectors.joining("&"));
            final var queryString = "?" + params;

            // when/then
            validateError(
                    () -> restClient
                            .get()
                            .uri(queryString, ACCOUNT_ID)
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    // Default JSR-303 message for @Size validation
                    "hookId size must be between 0 and " + MAX_REPEATED_QUERY_PARAMETERS);
        }

        @Test
        void invalidLimitTooLow() {
            validateError(
                    () -> restClient
                            .get()
                            .uri("?limit=0", ACCOUNT_ID)
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "limit must be greater than 0");
        }

        @Test
        void invalidLimitTooHigh() {
            validateError(
                    () -> restClient
                            .get()
                            .uri("?limit=101", ACCOUNT_ID)
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "limit must be less than or equal to 100");
        }

        @Test
        void invalidLimitFormat() {
            validateError(
                    () -> restClient
                            .get()
                            .uri("?limit=abc", ACCOUNT_ID)
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'limit'");
        }

        @Test
        void invalidOrder() {
            validateError(
                    () -> restClient
                            .get()
                            .uri("?order=foo", ACCOUNT_ID)
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'order'");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "?hook.id=lt:abc", // invalid number
                    "?hook.id=foo:123", // invalid operator
                    "?hook.id=lte123", // missing colon
                    "?hook.id=gt=123", // wrong separator
                    "?hook.id=gte::123" // double colon
                })
        void invalidHookIdFormat(String queryString) {
            validateError(
                    () -> restClient
                            .get()
                            .uri(queryString, ACCOUNT_ID)
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'hook.id'");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "abc", // non-numeric
                    "0..1", // malformed
                    "0.abc.1", // non-numeric realm
                    "-1", // negative
                    ".", // incomplete
                    "0.0.", // incomplete shard.realm
                    "1.2.3.4", // too many parts
                    "9999999999999999999999999" // overflow
                })
        void invalidAccountIdFormat(String invalidAccountId) {
            validateError(
                    () -> restClient.get().uri("", invalidAccountId).retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'ownerId'");
        }

        private void persistAccount(long accountId, String alias, String evmAddress) {
            final var aliasBytes = BaseEncoding.base32().omitPadding().decode(alias);

            final byte[] evmBytes;
            try {
                evmBytes = Hex.decodeHex(evmAddress);
            } catch (DecoderException e) {
                throw new RuntimeException(e);
            }

            domainBuilder
                    .entity()
                    .customize(e -> e.id(accountId)
                            .type(EntityType.ACCOUNT)
                            .alias(aliasBytes)
                            .evmAddress(evmBytes))
                    .persist();
        }

        private Hook persistHook(long ownerId) {
            return domainBuilder.hook().customize(hook -> hook.ownerId(ownerId)).persist();
        }
    }
}
