// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.web3.controller.OpcodesConcurrencyInterceptor.CONCURRENT_TRACE_LIMIT_EXCEEDED_MESSAGE;

import java.util.concurrent.Semaphore;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

final class OpcodesConcurrencyInterceptorTest {

    private static final Object HANDLER = new Object();

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void acquiresPermitWhenWithinLimit() {
        final var semaphore = new Semaphore(1);
        final var interceptor = new OpcodesConcurrencyInterceptor(semaphore);

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, HANDLER))
                .isTrue();
        assertThat(semaphore.availablePermits()).isZero();
    }

    @Test
    void rejectsWhenLimitExceeded() {
        final var interceptor = new OpcodesConcurrencyInterceptor(new Semaphore(0));

        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest(), response, HANDLER))
                .isInstanceOf(ThrottleException.class)
                .hasMessage(CONCURRENT_TRACE_LIMIT_EXCEEDED_MESSAGE);
    }

    @Test
    void releasesPermitOnCompletion() {
        final var semaphore = new Semaphore(1);
        final var interceptor = new OpcodesConcurrencyInterceptor(semaphore);
        final var request = new MockHttpServletRequest();

        interceptor.preHandle(request, response, HANDLER);
        interceptor.afterCompletion(request, response, HANDLER, null);

        assertThat(semaphore.availablePermits()).isEqualTo(1);
    }

    @Test
    void completionIsNoOpWhenPermitWasNeverAcquired() {
        final var semaphore = new Semaphore(1);
        final var interceptor = new OpcodesConcurrencyInterceptor(semaphore);

        interceptor.afterCompletion(new MockHttpServletRequest(), response, HANDLER, null);

        assertThat(semaphore.availablePermits()).isEqualTo(1);
    }

    @Test
    void permitIsReusableAcrossSequentialRequests() {
        final var semaphore = new Semaphore(1);
        final var interceptor = new OpcodesConcurrencyInterceptor(semaphore);
        final var firstRequest = new MockHttpServletRequest();
        final var secondRequest = new MockHttpServletRequest();

        interceptor.preHandle(firstRequest, response, HANDLER);
        interceptor.afterCompletion(firstRequest, response, HANDLER, null);

        assertThat(interceptor.preHandle(secondRequest, response, HANDLER)).isTrue();
        assertThat(semaphore.availablePermits()).isZero();
    }
}
