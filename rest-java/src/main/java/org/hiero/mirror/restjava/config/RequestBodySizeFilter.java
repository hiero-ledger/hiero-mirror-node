// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Named;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
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
 * Rejects requests whose body exceeds the configured maximum size before it is fully buffered into memory
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
        if (contentLength > maxRequestBodySize) {
            writeError(
                    response, "Request body %d exceeds maximum %d bytes".formatted(contentLength, maxRequestBodySize));
            return;
        }

        if (contentLength != 0) {
            filterChain.doFilter(new LimitedRequest(request, maxRequestBodySize), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod());
    }

    /**
     * Writes a {@code 413 Content Too Large} response in the same JSON format as {@code GenericControllerAdvice}. The
     * advice cannot be reused directly because it only handles exceptions raised within the DispatcherServlet, whereas
     * this filter runs outside of it, so the shared {@link ErrorResponseFactory} is used to keep the format consistent.
     */
    private void writeError(HttpServletResponse response, String detail) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        final var error = ErrorResponseFactory.create(HttpStatus.CONTENT_TOO_LARGE.getReasonPhrase(), detail);
        response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    /**
     * Wraps the request so its body cannot be read beyond the configured limit even when no {@code Content-Length} is
     * declared (chunked transfer encoding).
     */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;
        private LimitedInputStream inputStream;

        private LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long count;

        private LimitedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                increment(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            long remaining = maxBytes - count;
            int allowed = (int) Math.min(len, remaining + 1);
            int read = delegate.read(buffer, off, allowed);
            if (read > 0) {
                increment(read);
            }
            return read;
        }

        private void increment(int read) throws IOException {
            count += read;
            if (count > maxBytes) {
                throw new IOException("Request body exceeds the maximum allowed size of %d bytes".formatted(maxBytes));
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
