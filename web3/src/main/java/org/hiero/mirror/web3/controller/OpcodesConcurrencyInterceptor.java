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
 * Bounds how many {@code /opcodes} trace requests may be in flight at once. The permit is released in
 * {@link #afterCompletion} rather than a try/finally around the handler, so it stays held while a slow client is
 * still downloading the response.
 */
@RequiredArgsConstructor
final class OpcodesConcurrencyInterceptor implements HandlerInterceptor {

    private static final String PERMIT_ACQUIRED_ATTRIBUTE = OpcodesConcurrencyInterceptor.class.getName() + ".permit";

    static final String CONCURRENT_TRACE_LIMIT_EXCEEDED_MESSAGE =
            "Too many concurrent opcode trace requests, please retry later";

    private final Semaphore concurrentTraceLimiter;

    @Override
    public boolean preHandle(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final Object handler) {
        if (!concurrentTraceLimiter.tryAcquire()) {
            throw new ThrottleException(CONCURRENT_TRACE_LIMIT_EXCEEDED_MESSAGE);
        }
        request.setAttribute(PERMIT_ACQUIRED_ATTRIBUTE, Boolean.TRUE);
        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final Object handler,
            @Nullable final Exception exception) {
        if (Boolean.TRUE.equals(request.getAttribute(PERMIT_ACQUIRED_ATTRIBUTE))) {
            concurrentTraceLimiter.release();
        }
    }
}
