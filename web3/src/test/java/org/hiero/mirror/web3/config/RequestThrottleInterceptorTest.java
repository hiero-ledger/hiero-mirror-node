// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;
import static org.hiero.mirror.web3.ApiEndpointName.OPCODES;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE;

import org.hiero.mirror.web3.throttle.EndpointThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

final class RequestThrottleInterceptorTest {

    private EndpointThrottle endpointThrottle;
    private RequestThrottleInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        endpointThrottle = mock(EndpointThrottle.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        interceptor = new RequestThrottleInterceptor(endpointThrottle);
    }

    @Test
    void allowsRequestWithoutKnownEndpoint() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, "/unknown");
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void throttlesActionsEndpoint() {
        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, ACTIONS.getPath());

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(endpointThrottle).throttle(ACTIONS);
    }

    @Test
    void throttlesKnownEndpoint() {
        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, OPCODES.getPath());

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(endpointThrottle).throttle(OPCODES);
    }
}
