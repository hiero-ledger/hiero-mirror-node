// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import lombok.Getter;
import lombok.SneakyThrows;
import org.hiero.mirror.monitor.publish.PublishScenario;
import org.hiero.mirror.monitor.publish.PublishScenarioProperties;
import org.hiero.mirror.monitor.publish.generator.TransactionGenerator;
import org.hiero.mirror.monitor.subscribe.MirrorSubscriber;
import org.hiero.mirror.monitor.subscribe.Scenario;
import org.hiero.mirror.monitor.subscribe.TestScenario;
import org.hiero.mirror.monitor.subscribe.rest.RestApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClusterHealthIndicatorTest {

    @Mock
    private MirrorSubscriber mirrorSubscriber;

    @Mock
    private RestApiClient restApiClient;

    @Mock
    private TransactionGenerator transactionGenerator;

    @Mock
    private ReleaseHealthProperties releaseHealthProperties;

    @Mock
    private MeterRegistry meterRegistry;

    @InjectMocks
    private ClusterHealthIndicator clusterHealthIndicator;

    @ParameterizedTest
    @CsvSource({
        "1.0, 1.0, 200, UP, false", // healthy
        "0.0, 1.0, 200, UNKNOWN, false", // publishing inactive
        "1.0, 0.0, 200, UNKNOWN, false", // subscribing inactive
        "0.0, 0.0, 200, UNKNOWN, false", // publishing and subscribing inactive
        "1.0, 1.0, 400, UNKNOWN, false", // unknown network stake
        "1.0, 1.0, 500, DOWN, false", // network stake down
        "0.0, 0.0, 500, DOWN, false", // publishing and subscribing inactive and network stake down
        "0.0, 1.0, 500, DOWN, false", // network stake down and publishing inactive
        "1.0, 0.0, 500, DOWN, false", // network stake down and subscribing inactive
        "1.0, 1.0, 200, UP, true", // healthy
        "0.0, 1.0, 500, DOWN, true", // publishing inactive
        "1.0, 0.0, 500, DOWN, true", // subscribing inactive
        "0.0, 0.0, 500, DOWN, true", // publishing and subscribing inactive
        "1.0, 1.0, 400, UNKNOWN, true", // unknown network stake
        "1.0, 1.0, 500, DOWN, true", // network stake down
        "0.0, 0.0, 500, DOWN, true", // publishing and subscribing inactive and network stake down
        "0.0, 1.0, 500, DOWN, true", // network stake down and publishing inactive
        "1.0, 0.0, 500, DOWN, true", // network stake down and subscribing inactive
    })
    void health(
            double publishRate, double subscribeRate, int networkStatusCode, Status status, boolean failWhenInactive) {
        when(releaseHealthProperties.isFailWhenInactive()).thenReturn(failWhenInactive);
        when(transactionGenerator.scenarios()).thenReturn(Flux.just(publishScenario(publishRate)));
        when(mirrorSubscriber.getSubscriptions()).thenReturn(Flux.just(subscribeScenario(subscribeRate)));
        when(restApiClient.getNetworkStakeStatusCode())
                .thenReturn(Mono.just(HttpStatusCode.valueOf(networkStatusCode)));
        assertThat(clusterHealthIndicator.health().block())
                .extracting(Health::getStatus)
                .isEqualTo(status);
    }

    @SneakyThrows
    @Test
    void restNetworkStakeConnectException() {
        var exception = new WebClientRequestException(
                new ConnectException("Connection refused"),
                HttpMethod.GET,
                new URI("http://localhost/api/v1/network/stake"),
                HttpHeaders.EMPTY);
        when(restApiClient.getNetworkStakeStatusCode()).thenReturn(Mono.error(exception));
        assertThat(clusterHealthIndicator.health().block())
                .extracting(Health::getStatus)
                .isEqualTo(Status.DOWN);
    }

    @SneakyThrows
    @Test
    void restNetworkStakeTimeoutException() {
        when(transactionGenerator.scenarios()).thenReturn(Flux.just(publishScenario(1.0)));
        when(mirrorSubscriber.getSubscriptions()).thenReturn(Flux.just(subscribeScenario(1.0)));
        when(restApiClient.getNetworkStakeStatusCode())
                .thenReturn(Mono.delay(Duration.ofSeconds(6L)).thenReturn(HttpStatusCode.valueOf(200)));

        StepVerifier.withVirtualTime(() -> clusterHealthIndicator.health())
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextMatches(s -> s.getStatus() == Status.DOWN
                        && ((String) s.getDetails().get("reason")).contains("within 5000ms"))
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    void restNetworkStakeError() {
        when(restApiClient.getNetworkStakeStatusCode()).thenReturn(Mono.error(new RuntimeException("Test exception")));
        assertThat(clusterHealthIndicator.health().block())
                .extracting(Health::getStatus)
                .isEqualTo(Status.UNKNOWN);
    }

    private PublishScenario publishScenario(double rate) {
        return new TestPublishScenario(rate);
    }

    private Scenario<?, ?> subscribeScenario(double rate) {
        TestScenario testScenario = new TestScenario();
        testScenario.setRate(rate);
        return testScenario;
    }

    @Getter
    private static class TestPublishScenario extends PublishScenario {

        private final double rate;

        private TestPublishScenario(double rate) {
            super(new PublishScenarioProperties());
            this.rate = rate;
        }
    }
}
