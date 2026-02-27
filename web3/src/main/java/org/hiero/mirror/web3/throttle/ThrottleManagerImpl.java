// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import static org.hiero.mirror.web3.config.ThrottleConfiguration.GAS_LIMIT_BUCKET;
import static org.hiero.mirror.web3.config.ThrottleConfiguration.OPCODE_RATE_LIMIT_BUCKET;
import static org.hiero.mirror.web3.config.ThrottleConfiguration.RATE_LIMIT_BUCKET;

import io.github.bucket4j.Bucket;
import jakarta.inject.Named;
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

    @Qualifier(OPCODE_RATE_LIMIT_BUCKET)
    private final Bucket opcodeRateLimitBucket;

    private final ThrottleProperties throttleProperties;

    @Override
    public void throttle(ContractCallRequest request) {
        if (!rateLimitBucket.tryConsume(1)) {
            throw new ThrottleException(REQUEST_PER_SECOND_LIMIT_EXCEEDED);
        } else if (!gasLimitBucket.tryConsume(throttleProperties.scaleGas(request.getGas()))) {
            throw new ThrottleException(GAS_PER_SECOND_LIMIT_EXCEEDED);
        }

        for (var requestFilter : throttleProperties.getRequest()) {
            if (requestFilter.test(request)) {
                action(requestFilter, request);
            }
        }
    }

    @Override
    public void throttleOpcodeRequest() {
        if (!opcodeRateLimitBucket.tryConsume(1)) {
            throw new ThrottleException(REQUEST_PER_SECOND_LIMIT_EXCEEDED);
        }
    }

    @Override
    public void restore(long gas) {
        long tokens = throttleProperties.scaleGas(gas);
        if (tokens > 0) {
            gasLimitBucket.addTokens(tokens);
        }
    }

    private void action(RequestProperties filter, ContractCallRequest request) {
        switch (filter.getAction()) {
            case LOG -> log.info("{}", request);
            case REJECT -> throw new ThrottleException("Invalid request");
            case THROTTLE -> {
                if (!filter.getBucket().tryConsume(1)) {
                    throw new ThrottleException(REQUEST_PER_SECOND_LIMIT_EXCEEDED);
                }
            }
        }
    }
}
