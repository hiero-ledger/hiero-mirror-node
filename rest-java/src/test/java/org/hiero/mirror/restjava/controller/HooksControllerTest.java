// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
final class HooksControllerTest extends ControllerTest {

    private final HookMapper hookMapper;

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
            storeHook(accountId);
            return uriSpec.uri("");
        }

        @Test
        void success() {
            // given
            final var hook1 = storeHook(accountId);
            final var hook2 = storeHook(accountId);
            final var expectedHooks = hookMapper.map(List.of(hook2, hook1));
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);

            // when
            final var actual = restClient.get().uri("").retrieve().body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @Test
        void noHooksFound() {
            // given
            storeHook(accountId + 1); // Different account
            final var expectedHooks = hookMapper.map(Collections.emptyList());
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);

            // when
            final var actual = restClient.get().uri("").retrieve().body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @Test
        void orderAsc() {
            // given
            final var hook1 = storeHook(accountId);
            final var hook2 = storeHook(accountId);
            final var expectedHooks = hookMapper.map(List.of(hook1, hook2));
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);

            // when
            final var actual = restClient.get().uri("?order=asc").retrieve().body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
        }

        @Test
        void limitAndNextLink() {
            // given
            int limit = 2;
            storeHook(accountId);
            final var hook2 = storeHook(accountId);
            final var hook3 = storeHook(accountId);
            final var expectedHooks = hookMapper.map(List.of(hook3, hook2));
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);
            final var links = new Links();
            links.setNext(String.format(
                    "/api/v1/accounts/%d/hooks?limit=%d&hook.id=lt:%d", accountId, limit, hook2.getHookId()));
            expectedResponse.setLinks(links);

            // when
            final var actual =
                    restClient.get().uri("?limit=" + limit).retrieve().body(HooksResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expectedResponse);
            assertThat(actual.getHooks()).hasSize(limit);
            assertThat(actual.getLinks()).isNotNull();
            assertThat(actual.getLinks().getNext()).isEqualTo(links.getNext());
        }

        @ParameterizedTest
        @CsvSource(
                delimiter = '|',
                value = {
                    "'?hook.id=lt:{2}' | 1,0", // lt hook2's ID -> (hook1, hook0)
                    "'?hook.id=lte:{2}'| 2,1,0", // lte hook2's ID -> (hook2, hook1, hook0)
                    "'?hook.id=gt:{1}' | 3,2", // gt hook1's ID -> (hook3, hook2)
                    "'?hook.id=gte:{1}'| 3,2,1", // gte hook1's ID -> (hook3, hook2, hook1)
                    "'?hook.id=eq:{1}' | 1", // eq hook1's ID -> (hook1)
                })
        void hookIdBounds(String parameters, String expectedIndices) {
            // given
            final var hooks = new ArrayList<Hook>();
            for (int i = 0; i < 4; i++) {
                hooks.add(storeHook(accountId));
            }
            final var expectedHookList = Arrays.stream(expectedIndices.split(","))
                    .map(String::trim)
                    .mapToInt(Integer::parseInt)
                    .mapToObj(hooks::get)
                    .collect(Collectors.toList());
            final var expectedHooks = hookMapper.map(expectedHookList);
            final var expectedResponse = new HooksResponse();
            expectedResponse.setHooks(expectedHooks);

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
    }

    private Hook storeHook(long accountId) {
        return domainBuilder.hook().customize(hook -> hook.ownerId(accountId)).persist();
    }
}
