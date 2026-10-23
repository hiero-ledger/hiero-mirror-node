// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE;

import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.ApiEndpointName;
import org.hiero.mirror.web3.throttle.EndpointThrottle;
import org.springframework.web.servlet.HandlerInterceptor;

@Named
@RequiredArgsConstructor
final class RequestThrottleInterceptor implements HandlerInterceptor {

    private final EndpointThrottle endpointThrottle;

    @Override
    public boolean preHandle(
            final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        if (!(request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String requestMapping)) {
            return true;
        }

        final var endpoint = ApiEndpointName.fromPath(requestMapping);
        if (endpoint == null) {
            return true;
        }

        endpointThrottle.throttle(endpoint);
        return true;
    }
}
