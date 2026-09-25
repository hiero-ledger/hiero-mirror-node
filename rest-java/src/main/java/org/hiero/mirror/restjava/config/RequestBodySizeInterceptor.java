// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects requests whose body exceeds the configured maximum size before it is buffered into memory. A declared
 * {@code Content-Length} is required so the size can be enforced up front; the servlet container never reads past it, so
 * requests without one (chunked transfer encoding) are rejected rather than streamed. Rejections are thrown as
 * {@link ResponseStatusException} so that {@code GenericControllerAdvice} formats them consistently with every other
 * error, rather than duplicating the error format here.
 */
@Named
final class RequestBodySizeInterceptor implements HandlerInterceptor {

    private final long maxRequestBodySize;

    RequestBodySizeInterceptor(RestJavaProperties properties) {
        this.maxRequestBodySize = properties.getMaxRequestBodySize().toBytes();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        final long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            throw new ResponseStatusException(HttpStatus.LENGTH_REQUIRED, "Content-Length header is required");
        }

        if (contentLength > maxRequestBodySize) {
            throw new ResponseStatusException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "Request body %d exceeds maximum %d bytes".formatted(contentLength, maxRequestBodySize));
        }

        return true;
    }
}
