// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hiero.mirror.restjava.RestJavaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

final class RequestBodySizeInterceptorTest {

    private static final long MAX_BYTES = 1024L;

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final RequestBodySizeInterceptor interceptor = new RequestBodySizeInterceptor(properties());

    @Test
    void rejectsDeclaredOversizedContentLength() {
        var request = new MockHttpServletRequest("POST", "/api/v1/network/fees");
        request.setContent(new byte[(int) MAX_BYTES + 1]);

        // Thrown so GenericControllerAdvice renders it; the reason becomes the response detail verbatim.
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
                    assertThat(e.getReason()).isEqualTo("Request body 1025 exceeds maximum 1024 bytes");
                });
    }

    @Test
    void rejectsMissingContentLength() {
        // Undeclared length (chunked): rejected outright since the size cannot be enforced up front.
        var request = new MockHttpServletRequest("POST", "/api/v1/network/fees") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[(int) MAX_BYTES]);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.LENGTH_REQUIRED);
                    assertThat(e.getReason()).isEqualTo("Content-Length header is required");
                });
    }

    @Test
    void allowsBodyWithinLimit() {
        var request = new MockHttpServletRequest("POST", "/api/v1/network/fees");
        request.setContent(new byte[(int) MAX_BYTES]);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void skipsNonPostRequest() {
        // Only POST requests carry a body to guard, so no Content-Length is required for other methods.
        var request = new MockHttpServletRequest("GET", "/api/v1/network/fees");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    private static RestJavaProperties properties() {
        var properties = new RestJavaProperties();
        properties.setMaxRequestBodySize(DataSize.ofBytes(MAX_BYTES));
        return properties;
    }
}
