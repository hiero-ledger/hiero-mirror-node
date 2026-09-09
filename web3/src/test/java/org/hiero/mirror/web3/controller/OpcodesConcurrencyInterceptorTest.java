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

    // base=0, memory=0, stack=10, storage=0: a default (stack-only) request weighs 10.
    private final TraceWeightEstimator estimator = new TraceWeightEstimator(0, 0, 10, 0);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void acquiresRequestWeightWhenWithinBudget() {
        // Given
        final var semaphore = new Semaphore(10);
        final var interceptor = new OpcodesConcurrencyInterceptor(semaphore, estimator);

        // When
        final var admitted = interceptor.preHandle(new MockHttpServletRequest(), response, HANDLER);

        // Then
        assertThat(admitted).isTrue();
        assertThat(semaphore.availablePermits()).isZero();
    }

    @Test
    void rejectsWhenBudgetExceeded() {
        // Given
        final var interceptor = new OpcodesConcurrencyInterceptor(new Semaphore(9), estimator);

        // When / Then
        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest(), response, HANDLER))
                .isInstanceOf(ThrottleException.class)
                .hasMessage(CONCURRENT_TRACE_LIMIT_EXCEEDED_MESSAGE);
    }

    @Test
    void cheaperRequestsFitWhereAFullOneWouldNotHaveFit() {
        // Given
        final var semaphore = new Semaphore(10);
        final var interceptor = new OpcodesConcurrencyInterceptor(semaphore, estimator);
        final var defaultRequest = new MockHttpServletRequest();
        final var bareRequest = new MockHttpServletRequest();
        bareRequest.setParameter("stack", "false");

        // When
        final var defaultAdmitted = interceptor.preHandle(defaultRequest, response, HANDLER);
        final var bareAdmitted = interceptor.preHandle(bareRequest, response, HANDLER);

        // Then
        assertThat(defaultAdmitted).isTrue();
        assertThat(bareAdmitted).isTrue();
        assertThat(semaphore.availablePermits()).isZero();
    }

    @Test
    void releasesWeightOnCompletion() {
        // Given
        final var semaphore = new Semaphore(10);
        final var interceptor = new OpcodesConcurrencyInterceptor(semaphore, estimator);
        final var request = new MockHttpServletRequest();
        interceptor.preHandle(request, response, HANDLER);

        // When
        interceptor.afterCompletion(request, response, HANDLER, null);

        // Then
        assertThat(semaphore.availablePermits()).isEqualTo(10);
    }

    @Test
    void completionIsNoOpWhenWeightWasNeverAcquired() {
        // Given
        final var semaphore = new Semaphore(10);
        final var interceptor = new OpcodesConcurrencyInterceptor(semaphore, estimator);

        // When
        interceptor.afterCompletion(new MockHttpServletRequest(), response, HANDLER, null);

        // Then
        assertThat(semaphore.availablePermits()).isEqualTo(10);
    }

    @Test
    void budgetIsReusableAcrossSequentialRequests() {
        // Given
        final var semaphore = new Semaphore(10);
        final var interceptor = new OpcodesConcurrencyInterceptor(semaphore, estimator);
        final var firstRequest = new MockHttpServletRequest();
        final var secondRequest = new MockHttpServletRequest();
        interceptor.preHandle(firstRequest, response, HANDLER);
        interceptor.afterCompletion(firstRequest, response, HANDLER, null);

        // When
        final var admitted = interceptor.preHandle(secondRequest, response, HANDLER);

        // Then
        assertThat(admitted).isTrue();
        assertThat(semaphore.availablePermits()).isZero();
    }
}
