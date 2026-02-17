// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hiero.mirror.monitor.publish.generator.TransactionGenerator;
import org.hiero.mirror.monitor.subscribe.MirrorSubscriber;
import org.hiero.mirror.monitor.subscribe.Scenario;
import org.hiero.mirror.monitor.subscribe.rest.RestApiClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.boot.health.contributor.Status;
import reactor.core.publisher.Mono;

@CustomLog
@Named
@RequiredArgsConstructor
public class SubscriberHealthIndicator implements ReactiveHealthIndicator {
    private static final String METRIC_NAME = "hiero.mirror.monitor.health";
    private static final AtomicInteger CLUSTER_UP = new AtomicInteger(0);

    private static final Mono<Health> UNKNOWN = health(Status.UNKNOWN, "Publishing is inactive");
    private static final Mono<Health> UP = health(Status.UP, "");
    private static final Mono<Health> DOWN = health(Status.DOWN, "");

    private final ReleaseHealthProperties releaseHealthProperties;
    private final MirrorSubscriber mirrorSubscriber;
    private final RestApiClient restApiClient;
    private final TransactionGenerator transactionGenerator;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    private void registerGauge() {
        Gauge.builder(METRIC_NAME, CLUSTER_UP, AtomicInteger::get)
                .tag("type", "cluster")
                .register(meterRegistry);
    }

    private static Mono<Health> health(Status status, String reason) {
        Health.Builder health = Health.status(status);
        if (StringUtils.isNotBlank(reason)) {
            health.withDetail("reason", reason);
        }
        return Mono.just(health.build());
    }

    @Override
    public Mono<Health> health() {
        return restNetworkStakeHealth()
                .flatMap(health ->
                        health.getStatus() == Status.UP ? publishing().switchIfEmpty(subscribing()) : Mono.just(health))
                .doOnNext(this::recordHealthMetric);
    }

    private void recordHealthMetric(Health health) {
        final var status = health != null ? health.getStatus() : Status.UP;
        CLUSTER_UP.set(Status.UP.equals(status) ? 1 : 0);
    }

    // Returns down or unknown if all publish scenarios aggregated rate has dropped to zero, otherwise returns an empty
    // flux
    private Mono<Health> publishing() {
        return transactionGenerator
                .scenarios()
                .map(Scenario::getRate)
                .reduce(0.0, (c, n) -> c + n)
                .filter(sum -> sum <= 0)
                .flatMap(n -> getHealthForZeroRate());
    }

    // Returns up if any subscription is running and its rate is above zero, otherwise returns down or unknown
    private Mono<Health> subscribing() {
        return mirrorSubscriber
                .getSubscriptions()
                .map(Scenario::getRate)
                .reduce(0.0, (cur, next) -> cur + next)
                .filter(sum -> sum > 0)
                .flatMap(n -> UP)
                .switchIfEmpty(getHealthForZeroRate());
    }

    private Mono<Health> restNetworkStakeHealth() {
        return restApiClient
                .getNetworkStakeStatusCode()
                .flatMap(statusCode -> {
                    if (statusCode.is2xxSuccessful()) {
                        return UP;
                    }

                    var status = statusCode.is5xxServerError() ? Status.DOWN : Status.UNKNOWN;
                    var statusMessage =
                            String.format("Network stake status is %s with status code %s", status, statusCode.value());
                    log.error(statusMessage);
                    return health(status, statusMessage);
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    var status = Status.UNKNOWN;
                    // Connection issue can be caused by database being down, since the rest API service will become
                    // unavailable eventually
                    var rootCause = ExceptionUtils.getRootCause(e);
                    if (rootCause instanceof ConnectException || rootCause instanceof TimeoutException) {
                        status = Status.DOWN;
                    }

                    var statusMessage =
                            String.format("Network stake status is %s with error: %s", status, e.getMessage());
                    log.error(statusMessage);
                    return health(status, statusMessage);
                });
    }

    private Mono<Health> getHealthForZeroRate() {
        return releaseHealthProperties.isFailWhenInactive() ? DOWN : UNKNOWN;
    }
}
