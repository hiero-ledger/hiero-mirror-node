// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.SneakyThrows;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

final class RequestBodySizeFilterTest {

    private static final long MAX_BYTES = 1024L;

    private final MockFilterChain chain = new MockFilterChain();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final RequestBodySizeFilter filter = new RequestBodySizeFilter(properties());

    // Reads the request body to completion, exercising the streamed size cap the way the message converter would.
    private static final FilterChain READING_CHAIN = (request, response) ->
            ((HttpServletRequest) request).getInputStream().readAllBytes();

    @Test
    @SneakyThrows
    void rejectsDeclaredOversizedContentLength() {
        var request = new MockHttpServletRequest("POST", "/api/v1/network/fees");
        request.setContent(new byte[(int) MAX_BYTES + 1]);

        filter.doFilter(request, response, chain);

        // Rejected up front before the body is buffered; the chain is never invoked.
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @SneakyThrows
    void capsStreamedBodyWithoutContentLength() {
        // Undeclared length (chunked): the cap only takes effect as the oversized body is read.
        var request = new MockHttpServletRequest("POST", "/api/v1/network/fees") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[(int) MAX_BYTES + 1]);

        // The overrun surfaces as an IOException while reading; Spring maps that to a 400 for the request.
        assertThatThrownBy(() -> filter.doFilter(request, response, READING_CHAIN))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds the maximum allowed size");
    }

    @Test
    @SneakyThrows
    void allowsBodyWithinLimit() {
        var request = new MockHttpServletRequest("POST", "/api/v1/network/fees");
        var body = new byte[(int) MAX_BYTES];
        request.setContent(body);

        filter.doFilter(request, response, chain);

        // The wrapped request is passed through and its full body remains readable.
        assertThat(chain.getRequest()).isNotNull();
        assertThat(((HttpServletRequest) chain.getRequest()).getInputStream().readAllBytes())
                .hasSize(body.length);
    }

    @Test
    @SneakyThrows
    void skipsWrappingForBodylessRequest() {
        var request = new MockHttpServletRequest("GET", "/api/v1/network/fees");

        filter.doFilter(request, response, chain);

        // No body to cap, so the original request is forwarded unwrapped.
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static RestJavaProperties properties() {
        var properties = new RestJavaProperties();
        properties.setMaxRequestBodySize(DataSize.ofBytes(MAX_BYTES));
        return properties;
    }
}
