// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.healthcheck;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Dependency-free liveness probe for the container HEALTHCHECK.
// Exits 0 when the endpoint returns HTTP 200, otherwise 1.
public final class HealthCheckClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    static void main(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            System.err.println("Health check failed: URL argument is required");
            System.exit(1);
        }

        try (final var client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {

            final var request = HttpRequest.newBuilder()
                    .uri(URI.create(args[0]))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            final var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            System.exit(response.statusCode() == 200 ? 0 : 1);
        } catch (Exception e) {
            final var message = e.getMessage() != null ? e.getMessage() : "could not connect.";
            System.err.println("Health check failed: " + message);
            System.exit(1);
        }
    }
}
