// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.common.Constants.MAX_REPEATED_QUERY_PARAMETERS;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.Sort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
final class HooksControllerTest extends ControllerTest {

    private final HookMapper hookMapper;

    @LocalServerPort
    private int port;

    @DisplayName("/api/v1/accounts/{accountId}/hooks")
    @Nested
    final class HooksEndpointTest extends EndpointTest {

        private final long accountId = 1234L;

        @Override
        protected String getUrl() {
            return "accounts/" + accountId + "/hooks";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            // given: at least one persisted hook for default request
            persistHook(accountId);
            return uriSpec.uri("");
        }

        @Test
        void success() {
            // given
            final var hook1 = persistHook(accountId);
            final var hook2 = persistHook(accountId);
            final var expectedHooks = hookMapper.map(List.of(hook2, hook1));
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);
            expectedResponse.setLinks(new Links());

            // when
            final var actual = restClient.get().uri("").retrieve().body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @Test
        void noHooksFound() {
            // given
            persistHook(accountId + 1);
            final var expectedHooks = hookMapper.map(Collections.emptyList());
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);
            expectedResponse.setLinks(new Links());

            // when
            final var actual = restClient.get().uri("").retrieve().body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @Test
        void orderAsc() {
            final var hook1 = persistHook(accountId);
            final var hook2 = persistHook(accountId);

            assertHooksOrder(Sort.Direction.ASC, List.of(hook1, hook2));
        }

        @Test
        void orderDesc() {
            final var hook1 = persistHook(accountId);
            final var hook2 = persistHook(accountId);

            assertHooksOrder(Sort.Direction.DESC, List.of(hook2, hook1));
        }

        @Test
        void limitAndNextLink() {
            // given
            final int limit = 2;
            persistHook(accountId);
            final var hook2 = persistHook(accountId);
            final var hook3 = persistHook(accountId);
            final var expectedHooks = hookMapper.map(List.of(hook3, hook2));
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);
            final var links = new Links();
            links.setNext(String.format(
                    "/api/v1/accounts/%d/hooks?limit=%d&hook.id=gt:0&hook.id=lt:%d",
                    accountId, limit, hook2.getHookId()));
            expectedResponse.setLinks(links);

            // when
            final var actual = restClient
                    .get()
                    .uri("?limit=" + limit + "&hook.id=gt:0")
                    .retrieve()
                    .body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
            assertThat(actual.getHooks()).hasSize(limit);
            assertThat(actual.getLinks()).isNotNull();
            assertThat(actual.getLinks().getNext()).isEqualTo(links.getNext());
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
                hooks.add(persistHook(accountId));
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
            final var actual = restClient.get().uri(formattedParams).retrieve().body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @Test
        void hookIdWithMissingOperatorDefaultsToEquals() {
            // given
            persistHook(accountId); // Another hook, to be filtered out
            final var hook1 = persistHook(accountId);
            persistHook(accountId); // Another hook, to be filtered out

            final var expectedHooks = hookMapper.map(List.of(hook1));
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);
            expectedResponse.setLinks(new Links());

            // when
            final var queryString = "?hook.id=" + hook1.getHookId();
            final var actual = restClient.get().uri(queryString).retrieve().body(HooksResponse.class);

            // then
            // Verify it's not null and matches the expected structure
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
            // Explicitly verify only the one hook we asked for was returned
            assertThat(actual.getHooks()).hasSize(1);
            assertThat(actual.getHooks().get(0).getHookId()).isEqualTo(hook1.getHookId());
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
                    () -> restClient.get().uri(queryString).retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    // Default JSR-303 message for @Size validation
                    "hookIdFilters size must be between 0 and " + MAX_REPEATED_QUERY_PARAMETERS);
        }

        @Test
        void invalidLimitTooLow() {
            validateError(
                    () -> restClient.get().uri("?limit=0").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "limit must be greater than 0");
        }

        @Test
        void invalidLimitTooHigh() {
            validateError(
                    () -> restClient.get().uri("?limit=101").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "limit must be less than or equal to 100");
        }

        @Test
        void invalidLimitFormat() {
            validateError(
                    () -> restClient.get().uri("?limit=abc").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'limit'");
        }

        @Test
        void invalidOrder() {
            validateError(
                    () -> restClient.get().uri("?order=foo").retrieve().toEntity(String.class),
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
                    () -> restClient.get().uri(queryString).retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'hook.id'");
        }

        private void assertHooksOrder(Sort.Direction order, List<Hook> expectedHookOrder) {
            final var expectedHooks = hookMapper.map(expectedHookOrder);
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);
            expectedResponse.setLinks(new Links());

            // when
            final var actual = restClient
                    .get()
                    .uri("?order=" + order.name().toLowerCase())
                    .retrieve()
                    .body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }
    }

    @DisplayName("GET /api/v1/accounts/{accountId}/hooks [Valid Account ID Formats]")
    @ParameterizedTest
    @ValueSource(
            strings = {
                "1234", // num
                "0.1234", // realm.num
                "0.0.1234", // shard.realm.num
                // "HIQQEXWKW53RKN4W6XXC4Q232SYNZ3SZANVZZSUME5B5PRGXL663UAQA",     // alias only
                // "0.HIQQEXWKW53RKN4W6XXC4Q232SYNZ3SZANVZZSUME5B5PRGXL663UAQA",   // realm.alias
                // "0.1.HIQQEXWKW53RKN4W6XXC4Q232SYNZ3SZANVZZSUME5B5PRGXL663UAQA", // shard.realm.alias
                // "ac384c53f03855fa1b3616052f8ba32c6c2a2fec",                     // evm address
                // "0.0.ac384c53f03855fa1b3616052f8ba32c6c2a2fec"                  // shard.realm.evm address
            })
    void successWithDifferentAccountIdFormats(String accountIdString) {
        RestClient manualClient = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/v1/")
                .build();

        // given
        final long ownerId = 1234L;
        final var hook1 = persistHook(ownerId);
        final var hook2 = persistHook(ownerId);

        final var expectedHooks = hookMapper.map(List.of(hook2, hook1));
        final var expectedResponse = new HooksResponse();
        expectedResponse.setHooks(expectedHooks);
        expectedResponse.setLinks(new Links());

        // when
        final var url = "accounts/" + accountIdString + "/hooks";
        final var actual = manualClient.get().uri(url).retrieve().body(HooksResponse.class);

        // then
        assertThat(actual).isNotNull().isEqualTo(expectedResponse);
    }

    @DisplayName("GET /api/v1/accounts/{accountId}/hooks [Invalid Account ID Formats]")
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
        RestClient manualClient = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/v1/")
                .build();

        final var url = "accounts/" + invalidAccountId + "/hooks";

        validateError(
                () -> manualClient.get().uri(url).retrieve().toEntity(String.class),
                HttpClientErrorException.BadRequest.class,
                "Failed to convert 'accountId'");
    }

    private Hook persistHook(long ownerId) {
        return domainBuilder.hook().customize(hook -> hook.ownerId(ownerId)).persist();
    }
}
