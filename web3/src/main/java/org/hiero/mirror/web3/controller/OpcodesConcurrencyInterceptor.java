// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Bounds aggregate heap held by in-flight {@code /opcodes} traces, weighted by each request's capture size.
 * Released in {@link #afterCompletion} so it stays held while a slow client downloads the response.
 */
@RequiredArgsConstructor
final class OpcodesConcurrencyInterceptor implements HandlerInterceptor {

    private static final String TRACE_WEIGHT_ATTRIBUTE = OpcodesConcurrencyInterceptor.class.getName() + ".weight";

    static final String CONCURRENT_TRACE_LIMIT_EXCEEDED_MESSAGE =
            "Too many concurrent opcode trace requests, please retry later";

    private final Semaphore traceMemoryBudget;
    private final TraceWeightEstimator weightEstimator;

    @Override
    public boolean preHandle(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final Object handler) {
        final var weight = weightEstimator.estimate(request);
        if (!traceMemoryBudget.tryAcquire(weight)) {
            throw new ThrottleException(CONCURRENT_TRACE_LIMIT_EXCEEDED_MESSAGE);
        }
        request.setAttribute(TRACE_WEIGHT_ATTRIBUTE, weight);
        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final Object handler,
            @Nullable final Exception exception) {
        final var weight = (Integer) request.getAttribute(TRACE_WEIGHT_ATTRIBUTE);
        if (weight != null) {
            traceMemoryBudget.release(weight);
        }
    }
}
