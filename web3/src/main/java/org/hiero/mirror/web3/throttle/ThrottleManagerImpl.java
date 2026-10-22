// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import static org.hiero.mirror.web3.config.ThrottleConfiguration.GAS_LIMIT_BUCKET;
import static org.hiero.mirror.web3.config.ThrottleConfiguration.RATE_LIMIT_BUCKET;

import io.github.bucket4j.Bucket;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.springframework.beans.factory.annotation.Qualifier;

@CustomLog
@Named
@RequiredArgsConstructor
final class ThrottleManagerImpl implements ThrottleManager {

    static final String REQUEST_PER_SECOND_LIMIT_EXCEEDED = "Requests per second rate limit exceeded";
    static final String GAS_PER_SECOND_LIMIT_EXCEEDED = "Gas per second rate limit exceeded.";

    @Qualifier(GAS_LIMIT_BUCKET)
    private final Bucket gasLimitBucket;

    @Qualifier(RATE_LIMIT_BUCKET)
    private final Bucket rateLimitBucket;

    private final ThrottleProperties throttleProperties;

    @Override
    public void throttle(ContractCallRequest request) {
        if (!rateLimitBucket.tryConsume(1)) {
            throw new ThrottleException(REQUEST_PER_SECOND_LIMIT_EXCEEDED);
        }
        final long gasTokens = throttleProperties.scaleGas(request.getGas());
        if (!gasLimitBucket.tryConsume(gasTokens)) {
            rateLimitBucket.addTokens(1);
            throw new ThrottleException(GAS_PER_SECOND_LIMIT_EXCEEDED);
        }

        final var consumedFilters = new ArrayList<Bucket>();
        try {
            for (final var requestFilter : throttleProperties.getRequest()) {
                if (requestFilter.test(request)) {
                    action(requestFilter, request, consumedFilters);
                }
            }
        } catch (final ThrottleException exception) {
            rateLimitBucket.addTokens(1);
            if (gasTokens > 0) {
                gasLimitBucket.addTokens(gasTokens);
            }
            for (final var bucket : consumedFilters) {
                bucket.addTokens(1);
            }
            throw exception;
        }
    }

    @Override
    public void restore(long gas) {
        long tokens = throttleProperties.scaleGas(gas);
        if (tokens > 0) {
            gasLimitBucket.addTokens(tokens);
        }
    }

    @Override
    public void restoreRequest() {
        rateLimitBucket.addTokens(1);
    }

    private void action(RequestProperties filter, ContractCallRequest request, List<Bucket> consumedFilters) {
        switch (filter.getAction()) {
            case LOG -> log.info("{}", request);
            case REJECT -> throw new ThrottleException("Invalid request");
            case THROTTLE -> {
                final var bucket = filter.getBucket();
                if (!bucket.tryConsume(1)) {
                    throw new ThrottleException(REQUEST_PER_SECOND_LIMIT_EXCEEDED);
                }
                consumedFilters.add(bucket);
            }
        }
    }
}
