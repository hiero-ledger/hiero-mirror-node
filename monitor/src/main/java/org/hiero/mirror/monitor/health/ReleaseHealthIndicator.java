// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.micrometer.core.instrument.Gauge;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Strings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@CustomLog
@Named
@RequiredArgsConstructor
public class ReleaseHealthIndicator implements ReactiveHealthIndicator {
    private static final String METRIC_NAME = "hiero.mirror.monitor.release.health";
    private static final AtomicInteger RELEASE_UP = new AtomicInteger(0);

    static final String DEPENDENCY_NOT_READY = "DependencyNotReady";
    private static final Mono<Health> DOWN = Mono.just(Health.down().build());
    private static final Mono<Health> UNKNOWN = Mono.just(Health.unknown().build());
    private static final Mono<Health> UP = Mono.just(Health.up().build());
    private static final String INSTANCE_LABEL = "app.kubernetes.io/instance";
    private static final ResourceDefinitionContext RESOURCE_DEFINITION_CONTEXT = new ResourceDefinitionContext.Builder()
            .withGroup("helm.toolkit.fluxcd.io")
            .withKind("HelmRelease")
            .withNamespaced(true)
            .withPlural("helmreleases")
            .withVersion("v2")
            .build();
    private final KubernetesClient client;
    private final ReleaseHealthProperties properties;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Mono<Health> health = createHelmReleaseHealth();

    @PostConstruct
    private void registerGauge() {
        Gauge.builder(METRIC_NAME, RELEASE_UP, AtomicInteger::get).register(meterRegistry);
    }

    @Override
    public Mono<Health> health() {
        if (!properties.isEnabled()) {
            return UP;
        }

        return getHealth().doOnNext(this::recordHealthMetric);
    }

    private void recordHealthMetric(Health currentHealth) {
        final var status = currentHealth != null ? currentHealth.getStatus() : Status.UNKNOWN;
        RELEASE_UP.set(Status.UP.equals(status) ? 1 : 0);
    }

    private Mono<Health> createHelmReleaseHealth() {
        return Mono.fromCallable(this::getHelmRelease)
                .cacheInvalidateIf(v -> false)
                .doOnError(e -> log.error("Unable to get helm release", e))
                .onErrorComplete()
                .flatMap(this::getHelmReleaseReadyStatus)
                .doOnError(e -> log.error("Unable to get helm release ready status", e))
                .onErrorComplete()
                .switchIfEmpty(UNKNOWN)
                .cache(properties.getCacheExpiry(), Schedulers.newSingle("helmrelease-health-cache"));
    }

    private String getHelmRelease() {
        var hostname = System.getenv("HOSTNAME");
        var labels = client.pods().withName(hostname).get().getMetadata().getLabels();
        return Objects.requireNonNull(labels.get(INSTANCE_LABEL), "No " + INSTANCE_LABEL + " label");
    }

    @SuppressWarnings("unchecked")
    private Mono<Health> getHelmReleaseReadyStatus(String release) {
        var resource = client.genericKubernetesResources(RESOURCE_DEFINITION_CONTEXT)
                .withName(release)
                .get();
        var status = (Map<String, Object>) resource.getAdditionalProperties().get("status");
        var conditions = (List<Map<String, String>>) status.get("conditions");
        return conditions.stream()
                .filter(condition -> Strings.CS.equals(condition.get("type"), "Ready"))
                .findFirst()
                .map(this::mapStatus)
                .orElse(DOWN)
                .doOnNext(h -> log.info("Release status: {}", h));
    }

    private Mono<Health> mapStatus(Map<String, String> condition) {
        var status = condition.get("status");
        if ("True".equals(status)) {
            return UP;
        }

        var reason = condition.get("reason");
        if (DEPENDENCY_NOT_READY.equals(reason)) {
            return UNKNOWN;
        }

        return DOWN;
    }
}
