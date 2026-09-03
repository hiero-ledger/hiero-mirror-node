// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.web3.ApiEndpointName.OPCODES;
import static org.hiero.mirror.web3.ApiEndpointName.PRESTATE;
import static org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE;

import org.hiero.mirror.web3.ApiEndpointName;
import org.hiero.mirror.web3.ApiProperties;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestThrottleInterceptorTest {

    private RequestThrottleInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Web3Properties web3Properties;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        web3Properties = new Web3Properties();
        interceptor = new RequestThrottleInterceptor(web3Properties);
    }

    @Test
    void allowsRequestWithoutKnownEndpoint() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, "/unknown");
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void allowsRequestWhenEndpointThrottleIsDisabled() {
        setEndpointThrottle(OPCODES, 0);
        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, OPCODES.getPath());

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void throttlesConfiguredEndpoint() {
        setEndpointThrottle(OPCODES, 1);
        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, OPCODES.getPath());

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining("Requests per second rate limit exceeded");
    }

    @Test
    void throttlesEachEndpointUsingItsSeparateBucket() {
        setEndpointThrottle(OPCODES, 1);
        setEndpointThrottle(PRESTATE, 1);

        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, OPCODES.getPath());
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining("Requests per second rate limit exceeded");

        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, PRESTATE.getPath());
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining("Requests per second rate limit exceeded");
    }

    private void setEndpointThrottle(final ApiEndpointName endpoint, final long rateLimit) {
        final var apiProperties = new ApiProperties();
        apiProperties.getRequest().setThrottle(rateLimit);
        web3Properties.getApi().put(endpoint, apiProperties);
    }
}
