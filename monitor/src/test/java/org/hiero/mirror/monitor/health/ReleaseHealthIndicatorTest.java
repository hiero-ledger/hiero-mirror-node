// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.monitor.health.ReleaseHealthIndicator.DEPENDENCY_NOT_READY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@EnableKubernetesMockClient
@ExtendWith(SystemStubsExtension.class)
class ReleaseHealthIndicatorTest {

    private static final String HELM_RELEASE_PATH =
            "/apis/helm.toolkit.fluxcd.io/v2/namespaces/test/helmreleases/mirror";
    private static final String HOSTNAME = "test-pod";
    private static final String POD_REQUEST_PATH = "/api/v1/namespaces/test/pods/" + HOSTNAME;
    private static final Map<String, String> POD_LABELS = Map.of("app.kubernetes.io/instance", "mirror");

    @SystemStub
    private final EnvironmentVariables environmentVariables = new EnvironmentVariables("HOSTNAME", HOSTNAME);

    private final ReleaseHealthProperties properties = new ReleaseHealthProperties();

    private KubernetesMockServer server;
    private ReleaseHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        var client = server.createClient().inNamespace("test");
        healthIndicator = new ReleaseHealthIndicator(client, properties);
        properties.setEnabled(true);
        server.clearExpectations();
    }

    @Test
    void disabled() {
        // given
        properties.setEnabled(false);

        // then
        var health = healthIndicator.health().block();

        // then
        assertThat(health).returns(Status.UP, Health::getStatus);
    }

    @Test
    void down() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("False"))))
                .always();

        // when
        var health = healthIndicator.health().block();

        // then
        assertThat(health).returns(Status.DOWN, Health::getStatus);
    }

    @Test
    void dependencyNotReady() {
        // given
        var condition = readyCondition("False");
        condition.withReason(DEPENDENCY_NOT_READY);
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(condition)))
                .always();

        // when
        var health = healthIndicator.health().block();

        // then
        assertThat(health).returns(Status.UNKNOWN, Health::getStatus);
    }

    @Test
    void up() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("True"))))
                .always();

        // when
        var health = healthIndicator.health().block();

        // then
        assertThat(health).returns(Status.UP, Health::getStatus);
    }

    @Test
    void helmReleaseNotFound() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect().withPath(HELM_RELEASE_PATH).andReturn(404, null).always();

        // when
        var health = healthIndicator.health().block();

        // then
        assertThat(health).returns(Status.UNKNOWN, Health::getStatus);
    }

    @Test
    void helmReleaseWithoutReadyCondition() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(Collections.emptyList()))
                .always();

        // when
        var health = healthIndicator.health().block();

        // then
        assertThat(health).returns(Status.DOWN, Health::getStatus);
    }

    @Test
    void podLabelNotFound() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(Collections.emptyMap()))
                .always();

        // when
        var health = healthIndicator.health().block();

        // then
        assertThat(health).returns(Status.UNKNOWN, Health::getStatus);
    }

    @Test
    void podNotFound() {
        // given
        server.expect().withPath(POD_REQUEST_PATH).andReturn(404, null).always();

        // when
        var health = healthIndicator.health().block();

        // then
        assertThat(health).returns(Status.UNKNOWN, Health::getStatus);
    }

    @Test
    void cacheHit() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("True"))))
                .once();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("False"))))
                .once();

        // when, then
        // the flux concats the same hot mono (cached with expiry), when the first mono completes, the concat will
        // immediately subscribe to the second mono, so we need to 1) set the initial number of items to fetch to 0;
        // 2) await a little less than the expiry time before requesting 2 items
        StepVerifier.withVirtualTime(() -> Flux.concat(healthIndicator.health(), healthIndicator.health()), 0)
                .thenAwait(properties.getCacheExpiry().minus(Duration.ofMillis(10)))
                .thenRequest(2)
                .expectNext(Health.up().build(), Health.up().build())
                .verifyComplete();
    }

    @Test
    void cacheExpired() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("True"))))
                .once();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("False"))))
                .once();

        // when, then
        // the flux concats the same hot mono (cached with expiry), when the first mono completes, the concat will
        // immediately subscribe to the second mono, so we need to 1) set the initial number of items to fetch to 0;
        // 2) await exactly expiry time before requesting items
        StepVerifier.withVirtualTime(() -> Flux.concat(healthIndicator.health(), healthIndicator.health()), 0)
                .thenAwait(properties.getCacheExpiry())
                .thenRequest(2)
                .expectNext(Health.up().build(), Health.down().build())
                .verifyComplete();
    }

    @Test
    void sameMono() {
        var first = healthIndicator.health();
        var second = healthIndicator.health();
        assertEquals(first, second);
    }

    private GenericKubernetesResource helmRelease(List<ConditionBuilder> conditions) {
        var builtConditions = conditions.stream().map(ConditionBuilder::build).toList();
        Map<String, Object> statusProperties = Map.of("status", Map.of("conditions", builtConditions));
        return new GenericKubernetesResourceBuilder()
                .withKind("HelmRelease")
                .withAdditionalProperties(statusProperties)
                .build();
    }

    private Pod pod(Map<String, String> labels) {
        return new PodBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withLabels(labels)
                        .withName(HOSTNAME)
                        .build())
                .build();
    }

    private ConditionBuilder readyCondition(String status) {
        return new ConditionBuilder()
                .withType("Ready")
                .withReason("retries exhausted")
                .withStatus(status);
    }
}
