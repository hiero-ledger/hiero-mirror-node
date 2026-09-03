// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import static org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.ApiEndpointName;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.springframework.web.servlet.HandlerInterceptor;

@Named
@RequiredArgsConstructor
public final class RequestThrottleInterceptor implements HandlerInterceptor {

    private static final String REQUEST_PER_SECOND_LIMIT_EXCEEDED = "Requests per second rate limit exceeded";

    private final Map<ThrottleKey, Bucket> buckets = new ConcurrentHashMap<>();
    private final Web3Properties web3Properties;

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

        final long rateLimit = web3Properties.getApi(endpoint).getRequest().getThrottle();
        if (rateLimit == 0) {
            return true;
        }

        final var key = new ThrottleKey(endpoint, rateLimit);
        final var bucket = buckets.computeIfAbsent(key, ignored -> createBucket(rateLimit));
        if (!bucket.tryConsume(1)) {
            throw new ThrottleException(REQUEST_PER_SECOND_LIMIT_EXCEEDED);
        }

        return true;
    }

    private Bucket createBucket(final long rateLimit) {
        final var limit = Bandwidth.builder()
                .capacity(rateLimit)
                .refillGreedy(rateLimit, Duration.ofSeconds(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private record ThrottleKey(ApiEndpointName endpoint, long rateLimit) {}
}
