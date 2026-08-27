// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import jakarta.inject.Named;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.CustomLog;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests whose body exceeds the configured maximum size before it is fully buffered into memory
 */
@CustomLog
@Named
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestBodySizeFilter extends OncePerRequestFilter {

    private final long maxRequestBodySize;

    RequestBodySizeFilter(RestJavaProperties properties) {
        this.maxRequestBodySize = properties.getMaxRequestBodySize().toBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final long start = System.currentTimeMillis();
        final long contentLength = request.getContentLengthLong();
        if (contentLength > maxRequestBodySize) {
            log.info(
                    "{} {} {} in {} ms: {} Request body {} exceeds maximum {} bytes",
                    request.getRemoteAddr(),
                    request.getMethod(),
                    request.getRequestURI(),
                    System.currentTimeMillis() - start,
                    HttpStatus.CONTENT_TOO_LARGE.value(),
                    contentLength,
                    maxRequestBodySize);
            if (!response.isCommitted()) {
                response.sendError(HttpStatus.CONTENT_TOO_LARGE.value());
            }
            return;
        }

        if (hasBody(request, contentLength)) {
            filterChain.doFilter(new LimitedRequest(request, maxRequestBodySize), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private static boolean hasBody(HttpServletRequest request, long contentLength) {
        return HttpMethod.POST.matches(request.getMethod()) && contentLength != 0;
    }

    /**
     * Wraps the request so its body cannot be read beyond the configured limit even when no {@code Content-Length} is
     * declared (chunked transfer encoding).
     */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;

        private LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedInputStream(super.getInputStream(), maxBytes);
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
            int read = delegate.read(buffer, off, len);
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
