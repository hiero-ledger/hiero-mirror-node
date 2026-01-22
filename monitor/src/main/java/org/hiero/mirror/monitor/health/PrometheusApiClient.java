// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

@Named
class PrometheusApiClient {
    private final ImporterLagHealthProperties lagProperties;
    private final WebClient prometheusClient;

    PrometheusApiClient(final ImporterLagHealthProperties lagProperties, final WebClient.Builder webClientBuilder) {
        this.lagProperties = lagProperties;
        final var factory = new DefaultUriBuilderFactory(lagProperties.getPrometheusBaseUrl());
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);

        this.prometheusClient = webClientBuilder
                .baseUrl(lagProperties.getPrometheusBaseUrl())
                .uriBuilderFactory(factory)
                .defaultHeaders(h -> {
                    final var username = StringUtils.trimToNull(lagProperties.getPrometheusUsername());
                    final var password = StringUtils.trimToNull(lagProperties.getPrometheusPassword());
                    if (username != null && password != null) {
                        h.setBasicAuth(username, password);
                    }
                })
                .build();
    }

    Mono<PrometheusQueryResponse> query(String query) {
        final var encoded = UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8);
        return prometheusClient
                .get()
                .uri(builder -> builder.queryParam("query", encoded).build())
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError, resp -> resp.createException().flatMap(Mono::error))
                .bodyToMono(PrometheusQueryResponse.class)
                .timeout(lagProperties.getTimeout());
    }

    record PrometheusQueryResponse(String status, PrometheusData data) {}

    record PrometheusData(String resultType, List<PrometheusSeries> result) {}

    record PrometheusSeries(PrometheusMetric metric, List<Object> value) {}

    record PrometheusMetric(String cluster) {}
}
