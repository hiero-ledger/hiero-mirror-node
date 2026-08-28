// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Named;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.hiero.mirror.restjava.controller.ErrorResponseFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests whose body exceeds the configured maximum size before it is fully buffered into memory. A declared
 * {@code Content-Length} is required so the size can be enforced up front; the servlet container never reads past it, so
 * requests without one (chunked transfer encoding) are rejected rather than streamed.
 */
@Named
@Order(Ordered.LOWEST_PRECEDENCE)
final class RequestBodySizeFilter extends OncePerRequestFilter {

    private final long maxRequestBodySize;
    private final ObjectMapper objectMapper;

    RequestBodySizeFilter(RestJavaProperties properties, ObjectMapper objectMapper) {
        this.maxRequestBodySize = properties.getMaxRequestBodySize().toBytes();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            writeError(response, HttpStatus.LENGTH_REQUIRED, "Content-Length header is required");
            return;
        }

        if (contentLength > maxRequestBodySize) {
            writeError(
                    response,
                    HttpStatus.CONTENT_TOO_LARGE,
                    "Request body %d exceeds maximum %d bytes".formatted(contentLength, maxRequestBodySize));
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod());
    }

    /**
     * Writes an error response in the same JSON format as {@code GenericControllerAdvice}. The advice cannot be reused
     * directly because it only handles exceptions raised within the DispatcherServlet, whereas this filter runs outside
     * of it, so the shared {@link ErrorResponseFactory} is used to keep the format consistent.
     */
    private void writeError(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        final var error = ErrorResponseFactory.create(status.getReasonPhrase(), detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
