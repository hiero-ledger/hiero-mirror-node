// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import static org.hiero.mirror.web3.config.ThrottleConfiguration.GAS_LIMIT_BUCKET;
import static org.hiero.mirror.web3.config.ThrottleConfiguration.OPCODE_RATE_LIMIT_BUCKET;
import static org.hiero.mirror.web3.config.ThrottleConfiguration.RATE_LIMIT_BUCKET;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.inject.Named;
import java.time.Duration;
import lombok.CustomLog;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@CustomLog
@Named
final class ThrottleManagerImpl implements ThrottleManager {

    static final String REQUEST_PER_SECOND_LIMIT_EXCEEDED = "Requests per second rate limit exceeded";
    static final String GAS_PER_SECOND_LIMIT_EXCEEDED = "Gas per second rate limit exceeded.";
    static final String UNKNOWN_CLIENT_IP = "unknown";

    private final Bucket gasLimitBucket;
    private final Bucket rateLimitBucket;
    private final Bucket opcodeRateLimitBucket;
    private final ThrottleProperties throttleProperties;
    private final Cache<String, Bucket> ipBuckets;

    ThrottleManagerImpl(
            @Qualifier(GAS_LIMIT_BUCKET) Bucket gasLimitBucket,
            @Qualifier(RATE_LIMIT_BUCKET) Bucket rateLimitBucket,
            @Qualifier(OPCODE_RATE_LIMIT_BUCKET) Bucket opcodeRateLimitBucket,
            ThrottleProperties throttleProperties) {
        this.gasLimitBucket = gasLimitBucket;
        this.rateLimitBucket = rateLimitBucket;
        this.opcodeRateLimitBucket = opcodeRateLimitBucket;
        this.throttleProperties = throttleProperties;
        this.ipBuckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(1))
                .maximumSize(throttleProperties.getIpBucketCacheSize())
                .build();
    }

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
        throttlePerIp();
    }

    @Override
    public void throttleOpcodeRequest() {
        if (!opcodeRateLimitBucket.tryConsume(1)) {
            throw new ThrottleException(REQUEST_PER_SECOND_LIMIT_EXCEEDED);
        }
        throttlePerIp();
    }

    @Override
    public void restore(long gas) {
        long tokens = throttleProperties.scaleGas(gas);
        if (tokens > 0) {
            gasLimitBucket.addTokens(tokens);
        }
    }

    private void throttlePerIp() {
        if (!ipBucket(clientIp()).tryConsume(1)) {
            throw new ThrottleException(REQUEST_PER_SECOND_LIMIT_EXCEEDED);
        }
    }

    private Bucket ipBucket(String clientIp) {
        return ipBuckets.get(clientIp, this::newIpBucket);
    }

    private Bucket newIpBucket(String unused) {
        long rate = throttleProperties.getRequestsPerSecondPerIp();
        final var limit = Bandwidth.builder()
                .capacity(rate)
                .refillGreedy(rate, Duration.ofSeconds(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var ip = attributes.getRequest().getRemoteAddr();
            if (ip != null && !ip.isBlank()) {
                return ip;
            }
        }
        return UNKNOWN_CLIENT_IP;
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
