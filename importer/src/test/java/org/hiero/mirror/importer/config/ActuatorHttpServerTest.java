// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
final class ActuatorHttpServerTest {

    @Mock
    private Function<String, Status> healthResolver;

    @Mock
    private PrometheusMeterRegistry prometheusMeterRegistry;

    private ActuatorHttpServer actuatorHttpServer;
    private HttpClient httpClient;
    private int port;

    @BeforeEach
    void setup() throws Exception {
        actuatorHttpServer = new ActuatorHttpServer(healthResolver, prometheusMeterRegistry, new ObjectMapper());
        // port 0 lets the OS pick a free port
        actuatorHttpServer.setPort(0);
        actuatorHttpServer.afterPropertiesSet();
        port = actuatorHttpServer.getPort();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void teardown() {
        actuatorHttpServer.destroy();
    }

    @Test
    void livenessReturns200WhenUp() throws Exception {
        when(healthResolver.apply("liveness")).thenReturn(Status.UP);

        final var response = get("/actuator/health/liveness");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void readinessReturns503WhenDown() throws Exception {
        when(healthResolver.apply("readiness")).thenReturn(Status.DOWN);

        final var response = get("/actuator/health/readiness");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"status\":\"DOWN\"");
    }

    @Test
    void startupReturns503WhenDown() throws Exception {
        when(healthResolver.apply("startup")).thenReturn(Status.DOWN);

        final var response = get("/actuator/health/startup");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"status\":\"DOWN\"");
    }

    @Test
    void livenessReturns503WhenResolverReturnsNull() throws Exception {
        when(healthResolver.apply("liveness")).thenReturn(null);

        final var response = get("/actuator/health/liveness");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"status\":\"DOWN\"");
    }

    @Test
    void prometheusReturnsMetrics() throws Exception {
        when(prometheusMeterRegistry.scrape()).thenReturn("# HELP jvm_memory_used_bytes\njvm_memory_used_bytes 1024\n");

        final var response = get("/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("text/plain; version=0.0.4; charset=utf-8");
        assertThat(response.body()).contains("jvm_memory_used_bytes");
    }

    @Test
    void nonGetReturns405() throws Exception {
        final var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/health/liveness"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(405);
    }

    private HttpResponse<String> get(String path) throws Exception {
        final var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
