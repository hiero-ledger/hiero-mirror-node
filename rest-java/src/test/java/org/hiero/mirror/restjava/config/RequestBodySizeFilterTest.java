// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

final class RequestBodySizeFilterTest {

    private static final long MAX_BYTES = 1024L;

    private final MockFilterChain chain = new MockFilterChain();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final RequestBodySizeFilter filter = new RequestBodySizeFilter(properties(), new ObjectMapper());

    @Test
    @SneakyThrows
    void rejectsDeclaredOversizedContentLength() {
        var request = new MockHttpServletRequest("POST", "/api/v1/network/fees");
        request.setContent(new byte[(int) MAX_BYTES + 1]);

        filter.doFilter(request, response, chain);

        // Rejected up front before the body is buffered; the chain is never invoked.
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        assertThat(chain.getRequest()).isNull();
        // The body matches the GenericControllerAdvice error format rather than the container default page.
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"_status\"")
                .contains(HttpStatus.CONTENT_TOO_LARGE.getReasonPhrase())
                .doesNotContain("\"data\"");
    }

    @Test
    @SneakyThrows
    void rejectsMissingContentLength() {
        // Undeclared length (chunked): rejected outright since the size cannot be enforced up front.
        var request = new MockHttpServletRequest("POST", "/api/v1/network/fees") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[(int) MAX_BYTES]);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.LENGTH_REQUIRED.value());
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"_status\"")
                .contains(HttpStatus.LENGTH_REQUIRED.getReasonPhrase())
                .doesNotContain("\"data\"");
    }

    @Test
    @SneakyThrows
    void allowsBodyWithinLimit() {
        var request = new MockHttpServletRequest("POST", "/api/v1/network/fees");
        var body = new byte[(int) MAX_BYTES];
        request.setContent(body);

        filter.doFilter(request, response, chain);

        // The request is passed through unwrapped and its full body remains readable.
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(((HttpServletRequest) chain.getRequest()).getInputStream().readAllBytes())
                .hasSize(body.length);
    }

    @Test
    @SneakyThrows
    void skipsNonPostRequest() {
        var request = new MockHttpServletRequest("GET", "/api/v1/network/fees");

        filter.doFilter(request, response, chain);

        // Only POST requests are filtered; the original request is forwarded untouched.
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static RestJavaProperties properties() {
        var properties = new RestJavaProperties();
        properties.setMaxRequestBodySize(DataSize.ofBytes(MAX_BYTES));
        return properties;
    }
}
