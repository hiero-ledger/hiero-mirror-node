// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.ApiEndpointName;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.exception.ThrottleException;

@Named
@RequiredArgsConstructor
public final class EndpointThrottle {

    private static final String REQUEST_PER_SECOND_LIMIT_EXCEEDED = "Requests per second rate limit exceeded";

    private final Map<ApiEndpointName, Bucket> buckets = new ConcurrentHashMap<>();
    private final Web3Properties web3Properties;

    public void throttle(final ApiEndpointName endpoint) {
        final long rateLimit = web3Properties.getApi(endpoint).getRequest().getThrottle();
        if (rateLimit == 0) {
            return;
        }

        final var bucket = buckets.computeIfAbsent(endpoint, ignored -> createBucket(rateLimit));
        if (!bucket.tryConsume(1)) {
            throw new ThrottleException(REQUEST_PER_SECOND_LIMIT_EXCEEDED);
        }
    }

    private Bucket createBucket(final long rateLimit) {
        final var limit = Bandwidth.builder()
                .capacity(rateLimit)
                .refillGreedy(rateLimit, Duration.ofSeconds(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
