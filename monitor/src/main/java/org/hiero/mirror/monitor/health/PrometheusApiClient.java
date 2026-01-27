// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriUtils;

@Named
final class PrometheusApiClient {

    private final RestClient prometheusClient;

    PrometheusApiClient(final ImporterLagHealthProperties lagProperties, final RestClient.Builder restClientBuilder) {
        final var factory = new DefaultUriBuilderFactory(lagProperties.getPrometheusBaseUrl());
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);

        final var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(lagProperties.getTimeout());
        requestFactory.setReadTimeout(lagProperties.getTimeout());

        this.prometheusClient = restClientBuilder
                .baseUrl(lagProperties.getPrometheusBaseUrl())
                .uriBuilderFactory(factory)
                .requestFactory(requestFactory)
                .defaultHeaders(h -> {
                    final var username = StringUtils.trimToNull(lagProperties.getPrometheusUsername());
                    final var password = StringUtils.trimToNull(lagProperties.getPrometheusPassword());
                    if (username != null && password != null) {
                        h.setBasicAuth(username, password);
                    }
                })
                .build();
    }

    PrometheusQueryResponse query(final String query) {
        final var encoded = UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8);

        return prometheusClient
                .get()
                .uri(builder -> builder.queryParam("query", encoded).build())
                .retrieve()
                .body(PrometheusQueryResponse.class);
    }

    static record PrometheusQueryResponse(String status, PrometheusData data) {}

    static record PrometheusData(String resultType, List<PrometheusSeries> result) {}

    static record PrometheusSeries(PrometheusMetric metric, List<Object> value) {}

    static record PrometheusMetric(String cluster) {}
}
