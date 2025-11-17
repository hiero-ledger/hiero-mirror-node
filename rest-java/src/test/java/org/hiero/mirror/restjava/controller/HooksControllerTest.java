// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.common.Constants.MAX_REPEATED_QUERY_PARAMETERS;

import com.google.common.io.BaseEncoding;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.rest.model.HooksResponse;
import org.hiero.mirror.rest.model.HooksStorageResponse;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.restjava.mapper.HookMapper;
import org.hiero.mirror.restjava.mapper.HookStorageMapper;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;
import org.web3j.utils.Numeric;

@RequiredArgsConstructor
final class HooksControllerTest extends ControllerTest {

    private final HookMapper hookMapper;
    private final HookStorageMapper hookStorageMapper;

    @DisplayName("/api/v1/accounts/{accountId}/hooks")
    @Nested
    @DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
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

    @DisplayName("/api/v1/accounts/{accountId}/hooks/{hookId}/storage")
    @Nested
    final class HooksStorageEndpointTest extends EndpointTest {

        private static final long HOOK_ID = 2001L;
        private static final long OWNER_ID = 1001L;
        private static final String KEY1 = "0x0000000000000000000000000000000000000000000000000000000000000001";
        private static final String KEY2 = "0x0000000000000000000000000000000000000000000000000000000000000002";
        private static final String KEY3 = "0x0000000000000000000000000000000000000000000000000000000000000003";
        private static final String KEY4 = "0x0000000000000000000000000000000000000000000000000000000000000004";
        private static final String TIMESTAMP1 = "1726874345.123456781";
        private static final String TIMESTAMP2 = "1726874345.123456782";
        private static final String TIMESTAMP3 = "1726874345.123456783";
        private static final String TIMESTAMP4 = "1726874345.123456784";

        @Override
        protected String getUrl() {
            return "accounts/{account_id}/hooks/{hookId}/storage";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            persistHookStorage(OWNER_ID, HOOK_ID);

            return uriSpec.uri("", OWNER_ID, HOOK_ID);
        }

        @ParameterizedTest
        @CsvSource(
                // entity ID formats
                delimiter = '|',
                value = {
                    OWNER_ID + " | " + HOOK_ID,
                    "0." + OWNER_ID + " | " + HOOK_ID,
                    "0.0." + OWNER_ID + " | " + HOOK_ID,
                    // alias formats
                    ALIAS + " | " + HOOK_ID,
                    "0." + ALIAS + " | " + HOOK_ID,
                    "0.0." + ALIAS + " | " + HOOK_ID,
                    // evm formats
                    EVM_ADDRESS + " | " + HOOK_ID,
                    "0." + EVM_ADDRESS + " | " + HOOK_ID,
                    "0.0." + EVM_ADDRESS + " | " + HOOK_ID,
                    "0x" + EVM_ADDRESS + " | " + HOOK_ID,
                })
        void success(String ownerId, String hookId) {
            // given
            persistAccount(OWNER_ID, ALIAS, EVM_ADDRESS);

            final var hookStorage1 = persistHookStorage(OWNER_ID, HOOK_ID, KEY1);
            final var hookStorage2 = persistHookStorage(OWNER_ID, HOOK_ID, incrementHex(KEY1, 1));

            final var expectedHookStorage = hookStorageMapper.map(List.of(hookStorage1, hookStorage2));

            final var expectedResponse = new HooksStorageResponse();
            expectedResponse.setOwnerId(EntityId.of(OWNER_ID).toString());
            expectedResponse.setHookId(Long.parseLong(hookId));
            expectedResponse.setStorage(expectedHookStorage);
            expectedResponse.setLinks(new Links());

            // when
            final var actual =
                    restClient.get().uri("", ownerId, hookId).retrieve().body(HooksStorageResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @Test
        void noHookStorageFound() {
            // given
            persistHookStorage(OWNER_ID, HOOK_ID + 1, KEY1);
            final var expectedHookStorage = hookStorageMapper.map(Collections.emptyList());
            final var expectedResponse = new HooksStorageResponse();
            expectedResponse.setHookId(HOOK_ID);
            expectedResponse.setLinks(new Links());
            expectedResponse.setOwnerId(EntityId.of(OWNER_ID).toString());
            expectedResponse.setStorage(expectedHookStorage);

            // when
            final var actual =
                    restClient.get().uri("", OWNER_ID, HOOK_ID).retrieve().body(HooksStorageResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @ParameterizedTest
        @ValueSource(strings = {"asc", "desc"})
        void limitAndNextLink(String order) {
            // given
            final int limit = 2;

            final var hookStorage1 = persistHookStorage(OWNER_ID, HOOK_ID, KEY1);
            final var hookStorage2 = persistHookStorage(OWNER_ID, HOOK_ID, incrementHex(KEY1, 1));
            final var hookStorage3 = persistHookStorage(OWNER_ID, HOOK_ID, incrementHex(KEY1, 2));

            final var expectedHookStorageEntities =
                    order.equals("asc") ? List.of(hookStorage1, hookStorage2) : List.of(hookStorage3, hookStorage2);

            final var expectedHookStorage = hookStorageMapper.map(expectedHookStorageEntities);
            final var expectedResponse = new HooksStorageResponse();
            expectedResponse.setHookId(HOOK_ID);
            expectedResponse.setOwnerId(EntityId.of(OWNER_ID).toString());
            expectedResponse.setStorage(expectedHookStorage);

            final var lastHookStorage = expectedHookStorageEntities.getLast();
            final var operator = order.equals("asc") ? "gt" : "lt";

            final var links = new Links();
            final var nextLink = String.format(
                    "/api/v1/accounts/%d/hooks/%d/storage?limit=%d&order=%s&key=%s:%s",
                    OWNER_ID, HOOK_ID, limit, order, operator, Hex.encodeHexString(lastHookStorage.getKey()));
            links.setNext(nextLink);
            expectedResponse.setLinks(links);

            // when
            final var actual = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("limit", limit)
                            .queryParam("order", order)
                            .build(Map.of(
                                    "account_id", OWNER_ID,
                                    "hookId", HOOK_ID)))
                    .retrieve()
                    .body(HooksStorageResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
            assertThat(actual.getStorage()).hasSize(limit);
        }

        @ParameterizedTest
        @CsvSource({
            "asc,  gt:" + TIMESTAMP1,
            "asc,  gte:" + TIMESTAMP1,
            "desc, lt:" + TIMESTAMP4,
            "desc, lte:" + TIMESTAMP4
        })
        void limitAndNextLinkWithTimestamp(String order, String timestampFilter) {
            // given
            final int limit = 2;

            final var change1 = persistHookStorageChange(
                    OWNER_ID,
                    HOOK_ID,
                    incrementHex(KEY1, 0),
                    TimestampParameter.valueOf(TIMESTAMP1).value());
            final var change2 = persistHookStorageChange(
                    OWNER_ID,
                    HOOK_ID,
                    incrementHex(KEY1, 1),
                    TimestampParameter.valueOf(TIMESTAMP1).value() + 1);
            final var change3 = persistHookStorageChange(
                    OWNER_ID,
                    HOOK_ID,
                    incrementHex(KEY1, 2),
                    TimestampParameter.valueOf(TIMESTAMP1).value() + 2);
            final var change4 = persistHookStorageChange(
                    OWNER_ID,
                    HOOK_ID,
                    incrementHex(KEY1, 3),
                    TimestampParameter.valueOf(TIMESTAMP1).value() + 3);

            final var hookStorageChanges = List.of(change1, change2, change3, change4);
            final long filterValue = TimestampParameter.valueOf(timestampFilter).value();
            final var operator = TimestampParameter.valueOf(timestampFilter).operator();

            final var filtered = hookStorageChanges.stream()
                    .filter(hs -> switch (operator) {
                        case GT -> hs.getConsensusTimestamp() > filterValue;
                        case GTE -> hs.getConsensusTimestamp() >= filterValue;
                        case LT -> hs.getConsensusTimestamp() < filterValue;
                        case LTE -> hs.getConsensusTimestamp() <= filterValue;
                        default -> false;
                    })
                    .toList();

            final Comparator<HookStorageChange> byKey =
                    Comparator.comparing(hs -> StringUtils.leftPad(Hex.encodeHexString(hs.getKey()), 64, '0'));

            final var ordered = order.equals("asc")
                    ? filtered.stream().sorted(byKey).toList()
                    : filtered.stream().sorted(byKey.reversed()).toList();

            final var limited = ordered.stream().limit(limit).toList();

            final var mapped = limited.stream()
                    .map(hs -> new HookStorage().hookStorage(hs))
                    .toList();

            final var expectedResponse = new HooksStorageResponse();
            expectedResponse.setHookId(HOOK_ID);
            expectedResponse.setOwnerId(EntityId.of(OWNER_ID).toString());
            expectedResponse.setStorage(hookStorageMapper.map(mapped));

            if (ordered.size() > limit) {
                final var last = limited.getLast();
                final var nextOperator = order.equals("asc") ? "gt" : "lt";

                final var links = new Links();
                final var next = String.format(
                        "/api/v1/accounts/%d/hooks/%d/storage?limit=%d&order=%s&timestamp=%s&key=%s:%s",
                        OWNER_ID,
                        HOOK_ID,
                        limit,
                        order,
                        timestampFilter,
                        nextOperator,
                        Hex.encodeHexString(last.getKey()));
                links.setNext(next);
                expectedResponse.setLinks(links);
            }

            // when
            final var actual = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("limit", limit)
                            .queryParam("order", order)
                            .queryParam("timestamp", timestampFilter)
                            .build(Map.of(
                                    "account_id", OWNER_ID,
                                    "hookId", HOOK_ID)))
                    .retrieve()
                    .body(HooksStorageResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
            assertThat(actual.getStorage()).hasSize(Math.min(limited.size(), limit));
        }

        static Stream<Arguments> provideKeyQueries() {
            return Stream.of(
                    // 'eq' or no-op (defaults to eq)
                    Arguments.of("?key=eq:" + KEY1, List.of(0)), // Single eq
                    Arguments.of("?key=" + KEY2, List.of(1)), // Single no-op
                    Arguments.of("?key=" + KEY2 + "&key=eq:" + KEY1, List.of(0, 1)), // Multiple eq/no-op
                    Arguments.of("?key=" + KEY2 + "&key=" + KEY1, List.of(0, 1)), // Multiple eq/no-op
                    Arguments.of("?key=eq:" + KEY2 + "&key=eq:" + KEY1, List.of(0, 1)), // Multiple eq
                    Arguments.of("?key=eq:" + KEY1 + "&key=eq:" + KEY1, List.of(0)), // Duplicate eq/no-op
                    // range only
                    Arguments.of("?key=lt:" + KEY4, List.of(0, 1, 2)), // Single lt
                    Arguments.of("?key=lte:" + KEY4, List.of(0, 1, 2, 3)), // Single lte
                    Arguments.of("?key=gt:" + KEY2, List.of(2, 3)), // Single gt
                    Arguments.of("?key=gte:" + KEY2, List.of(1, 2, 3)), // Single gte
                    Arguments.of("?key=lt:" + KEY4 + "&key=lt:" + KEY3, List.of(0, 1)), // Multiple lt
                    Arguments.of("?key=lt:" + KEY4 + "&key=lte:" + KEY2, List.of(0, 1)), // Multiple lt/lte
                    Arguments.of("?key=gt:" + KEY2 + "&key=gt:" + KEY3, List.of(3)), // Multiple gt
                    Arguments.of("?key=gt:" + KEY1 + "&key=gte:" + KEY2, List.of(1, 2, 3)), // Multiple gt/gte
                    Arguments.of("?key=gt:" + KEY1 + "&key=lt:" + KEY4, List.of(1, 2)), // Range (gt + lt)
                    Arguments.of("?key=gte:" + KEY1 + "&key=lte:" + KEY4, List.of(0, 1, 2, 3)), // Range (gte + lte)
                    Arguments.of("?key=gte:" + KEY3 + "&key=lte:" + KEY2, List.of()), // No overlap (gte + lte)
                    // 'eq'/'no-op' + range
                    Arguments.of("?key=eq:" + KEY2 + "&key=lte:" + KEY4, List.of(1)), // Eq + Lte overlap
                    Arguments.of("?key=eq:" + KEY2 + "&key=lt:" + KEY2, List.of()) //  No overlap (eq + lt)
                    );
        }

        @ParameterizedTest
        @MethodSource("provideKeyQueries")
        void hookStorageKeyBounds(String parameters, List<Integer> expectedIndices) {
            // given
            final var hookStorage = new ArrayList<HookStorage>();
            for (int i = 0; i < 4; i++) {
                hookStorage.add(persistHookStorage(OWNER_ID, HOOK_ID, incrementHex(KEY1, i)));
            }

            final List<HookStorage> expectedHookStorageList;
            if (expectedIndices == null || expectedIndices.isEmpty()) {
                expectedHookStorageList = Collections.emptyList();
            } else {
                expectedHookStorageList = expectedIndices.stream()
                        .map(hookStorage::get)
                        .sorted(Comparator.comparing(
                                hs -> StringUtils.leftPad(Hex.encodeHexString(hs.getKey()), 64, '0')))
                        .toList();
            }

            final var expectedHookStorage = hookStorageMapper.map(expectedHookStorageList);
            final var expectedResponse = new HooksStorageResponse();
            expectedResponse.setHookId(HOOK_ID);
            expectedResponse.setLinks(new Links());
            expectedResponse.setOwnerId(EntityId.of(OWNER_ID).toString());
            expectedResponse.setStorage(expectedHookStorage);

            // when
            final var actual = restClient
                    .get()
                    .uri(parameters, OWNER_ID, HOOK_ID)
                    .retrieve()
                    .body(HooksStorageResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        static Stream<Arguments> provideKeyTimestampQueries() {
            return Stream.of(

                    // timestamp-only (eq, lt, lte, gt, gte, no-op)
                    Arguments.of("?timestamp=eq:" + TIMESTAMP1, List.of(0)),
                    Arguments.of("?timestamp=lt:" + TIMESTAMP1, List.of()),
                    Arguments.of("?timestamp=lte:" + TIMESTAMP1, List.of(0)),
                    Arguments.of("?timestamp=gte:" + TIMESTAMP1, List.of(0, 1, 2, 3)),
                    Arguments.of("?timestamp=gt:" + TIMESTAMP1, List.of(1, 2, 3)),
                    Arguments.of("?timestamp=eq:" + TIMESTAMP2, List.of(0, 1)),
                    Arguments.of("?timestamp=" + TIMESTAMP3, List.of(0, 1, 2)),

                    // timestamp ranges
                    Arguments.of("?timestamp=gte:" + TIMESTAMP1 + "&timestamp=lte:" + TIMESTAMP4, List.of(0, 1, 2, 3)),
                    Arguments.of("?timestamp=gt:" + TIMESTAMP1 + "&timestamp=lt:" + TIMESTAMP4, List.of(1, 2)),
                    Arguments.of("?timestamp=gt:" + TIMESTAMP1 + "&timestamp=lte:" + TIMESTAMP4, List.of(1, 2, 3)),
                    Arguments.of("?timestamp=gte:" + TIMESTAMP1 + "&timestamp=lt:" + TIMESTAMP4, List.of(0, 1, 2)),
                    Arguments.of("?timestamp=gte:" + TIMESTAMP3 + "&timestamp=lte:" + TIMESTAMP2, List.of()),
                    Arguments.of("?timestamp=gt:" + TIMESTAMP2 + "&timestamp=lt:" + TIMESTAMP3, List.of()),

                    // key eq + timestamp ranges
                    Arguments.of(
                            "?key=eq:" + KEY2 + "&timestamp=gte:" + TIMESTAMP1 + "&timestamp=lte:" + TIMESTAMP4,
                            List.of(1)),
                    Arguments.of(
                            "?key=eq:" + KEY2 + "&timestamp=gt:" + TIMESTAMP1 + "&timestamp=lte:" + TIMESTAMP3,
                            List.of(1)),
                    Arguments.of("?key=eq:" + KEY2 + "&timestamp=lt:" + TIMESTAMP1, List.of()),

                    // timestamp eq + key ranges
                    Arguments.of("?timestamp=eq:" + TIMESTAMP2 + "&key=gte:" + KEY1, List.of(0, 1)),
                    Arguments.of("?timestamp=eq:" + TIMESTAMP3 + "&key=lte:" + KEY3, List.of(0, 1, 2)),

                    // mixed exclusive/inclusive boundaries
                    Arguments.of("?key=gt:" + KEY1 + "&timestamp=lt:" + TIMESTAMP3, List.of(1)),
                    Arguments.of("?key=lt:" + KEY3 + "&timestamp=gt:" + TIMESTAMP1, List.of(1)),
                    Arguments.of("?key=gt:" + KEY2 + "&timestamp=gte:" + TIMESTAMP3, List.of(2, 3)),

                    // timestamp no-op + key range
                    Arguments.of("?timestamp=" + TIMESTAMP1 + "&key=gte:" + KEY2 + "&key=lte:" + KEY4, List.of()),
                    Arguments.of("?timestamp=" + TIMESTAMP4 + "&key=lt:" + KEY3, List.of(0, 1)),

                    // out of range
                    Arguments.of("?key=gt:" + KEY3 + "&timestamp=lte:" + TIMESTAMP2, List.of()),
                    Arguments.of("?key=lte:" + KEY1 + "&timestamp=gt:" + TIMESTAMP2, List.of()),
                    Arguments.of("?key=gt:" + KEY3 + "&key=lt:" + KEY2, List.of()),
                    Arguments.of("?timestamp=gte:" + TIMESTAMP3 + "&timestamp=lt:" + TIMESTAMP2, List.of()),

                    // key eq + timestamp window covering everything
                    Arguments.of("?key=eq:" + KEY1 + "&timestamp=gte:" + TIMESTAMP1, List.of(0)),

                    // Wide key range + timestamp eq
                    Arguments.of(
                            "?key=gte:" + KEY1 + "&key=lte:" + KEY4 + "&timestamp=eq:" + TIMESTAMP3, List.of(0, 1, 2)),

                    // Unbounded keys + narrow timestamp window
                    Arguments.of("?timestamp=gt:" + TIMESTAMP1 + "&timestamp=lt:" + TIMESTAMP3, List.of(1)),
                    Arguments.of("?timestamp=gte:" + TIMESTAMP2 + "&timestamp=lte:" + TIMESTAMP3, List.of(1, 2)),

                    // Key gte/lte + timestamp gte/lte combinations
                    Arguments.of(
                            "?key=gte:" + KEY1 + "&key=lte:"
                                    + KEY4 + "&timestamp=gte:"
                                    + TIMESTAMP1 + "&timestamp=lte:"
                                    + TIMESTAMP4,
                            List.of(0, 1, 2, 3)),
                    Arguments.of(
                            "?key=gte:" + KEY1 + "&key=lte:"
                                    + KEY4 + "&timestamp=gte:"
                                    + TIMESTAMP1 + "&timestamp=lt:"
                                    + TIMESTAMP4,
                            List.of(0, 1, 2)),
                    Arguments.of(
                            "?key=gte:" + KEY1 + "&key=lte:"
                                    + KEY4 + "&timestamp=gt:"
                                    + TIMESTAMP1 + "&timestamp=lte:"
                                    + TIMESTAMP4,
                            List.of(1, 2, 3)),
                    Arguments.of(
                            "?key=gte:" + KEY2 + "&key=lte:"
                                    + KEY3 + "&timestamp=gte:"
                                    + TIMESTAMP1 + "&timestamp=lte:"
                                    + TIMESTAMP4,
                            List.of(1, 2)),
                    Arguments.of(
                            "?key=gte:" + KEY1 + "&key=lt:"
                                    + KEY3 + "&timestamp=gte:"
                                    + TIMESTAMP1 + "&timestamp=lte:"
                                    + TIMESTAMP4,
                            List.of(0, 1)),
                    Arguments.of(
                            "?key=gt:" + KEY1 + "&key=lte:"
                                    + KEY4 + "&timestamp=gte:"
                                    + TIMESTAMP1 + "&timestamp=lte:"
                                    + TIMESTAMP4,
                            List.of(1, 2, 3)));
        }

        @ParameterizedTest
        @MethodSource("provideKeyTimestampQueries")
        void timestampAndKeyBounds(String parameters, List<Integer> expectedIndices) {
            // given
            final var hookStorageChange = new ArrayList<HookStorageChange>();
            for (int i = 0; i < 4; i++) {
                hookStorageChange.add(persistHookStorageChange(
                        OWNER_ID,
                        HOOK_ID,
                        incrementHex(KEY1, i),
                        TimestampParameter.valueOf(TIMESTAMP1).value() + i));
            }

            final List<HookStorageChange> expectedHookStorageList;
            if (expectedIndices == null || expectedIndices.isEmpty()) {
                expectedHookStorageList = Collections.emptyList();
            } else {
                expectedHookStorageList = expectedIndices.stream()
                        .map(hookStorageChange::get)
                        .sorted(Comparator.comparing(
                                hs -> StringUtils.leftPad(Hex.encodeHexString(hs.getKey()), 64, '0')))
                        .toList();
            }

            final var list = expectedHookStorageList.stream()
                    .map(hs -> new HookStorage().hookStorage(hs))
                    .toList();
            final var expectedHookStorage = hookStorageMapper.map(list);
            final var expectedResponse = new HooksStorageResponse();
            expectedResponse.setHookId(HOOK_ID);
            expectedResponse.setLinks(new Links());
            expectedResponse.setOwnerId(EntityId.of(OWNER_ID).toString());
            expectedResponse.setStorage(expectedHookStorage);

            // when
            final var actual = restClient
                    .get()
                    .uri(parameters, OWNER_ID, HOOK_ID)
                    .retrieve()
                    .body(HooksStorageResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @Test
        void invalidLimitTooLow() {
            validateError(
                    () -> restClient
                            .get()
                            .uri("?limit=0", OWNER_ID, HOOK_ID)
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
                            .uri("?limit=101", OWNER_ID, HOOK_ID)
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
                            .uri("?limit=abc", OWNER_ID, HOOK_ID)
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
                            .uri("?order=foo", OWNER_ID, HOOK_ID)
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'order'");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "?key=lt:gbc", // invalid hex
                    "?key=foo:" + KEY1, // invalid operator
                    "?key=lte" + KEY1, // missing colon
                    "?key=gt=" + KEY1, // wrong separator
                    "?key=gte::" + KEY1 // double colon
                })
        void invalidKeyFormat(String queryString) {
            validateError(
                    () -> restClient
                            .get()
                            .uri(queryString, OWNER_ID, HOOK_ID)
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'key'");
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
                    () -> restClient
                            .get()
                            .uri("", invalidAccountId, HOOK_ID)
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'ownerId'");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "abc", // non-numeric
                    "0..1", // malformed
                    "0.abc.1", // non-numeric realm
                    ".", // incomplete
                    "0.0.", // incomplete shard.realm
                    "1.2.3.4", // too many parts
                    "9999999999999999999999999" // overflow
                })
        void invalidHookIdFormat(String invalidHookId) {
            validateError(
                    () -> restClient
                            .get()
                            .uri("", OWNER_ID, invalidHookId)
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'hookId'");
        }

        @Test
        void negativeHookId() {
            validateError(
                    () -> restClient.get().uri("", OWNER_ID, -1).retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "hookId must be greater than or equal to 0");
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

        private HookStorage persistHookStorage(long ownerId, long hookId) {
            return domainBuilder
                    .hookStorage()
                    .customize(hook -> hook.ownerId(ownerId).hookId(hookId))
                    .persist();
        }

        private HookStorage persistHookStorage(long ownerId, long hookId, String key) {
            return domainBuilder
                    .hookStorage()
                    .customize(hook -> hook.ownerId(ownerId).hookId(hookId).key(Numeric.hexStringToByteArray(key)))
                    .persist();
        }

        private HookStorageChange persistHookStorageChange(long ownerId, long hookId, String key, long timestamp) {
            return domainBuilder
                    .hookStorageChange()
                    .customize(hook -> hook.ownerId(ownerId)
                            .consensusTimestamp(timestamp)
                            .hookId(hookId)
                            .key(Numeric.hexStringToByteArray(key))
                            .ownerId(ownerId)
                            .valueRead(new byte[] {0x01, 0x02})
                            .valueWritten(new byte[] {0x03, 0x04}))
                    .persist();
        }

        private static String incrementHex(String hex, long amount) {
            if (amount < 0) {
                throw new IllegalArgumentException("Increment amount must be non-negative");
            }

            var value = new BigInteger(hex.replaceFirst("^(0x|0X)", ""), 16);
            value = value.add(BigInteger.valueOf(amount));

            return StringUtils.leftPad(value.toString(16), 64, '0');
        }
    }
}
