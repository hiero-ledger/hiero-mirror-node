// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import jakarta.inject.Named;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Named
final class PrometheusApiClient {

    private final RestClient prometheusClient;

    PrometheusApiClient(final ImporterLagHealthProperties lagProperties) {
        if (lagProperties.isEnabled()) {
            final var factory = new DefaultUriBuilderFactory(lagProperties.getPrometheusBaseUrl());
            // PromQL includes braces, quotes, etc. We want to pass it through as-is.
            factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);

            final var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(lagProperties.getTimeout());
            requestFactory.setReadTimeout(lagProperties.getTimeout());

            this.prometheusClient = RestClient.builder()
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
        } else {
            this.prometheusClient = null;
        }
    }

    PrometheusQueryResponse query(final String query) {
        return prometheusClient
                .get()
                .uri(builder -> builder.queryParam("query", query).build())
                .retrieve()
                .body(PrometheusQueryResponse.class);
    }

    record PrometheusQueryResponse(String status, PrometheusData data) {

        private boolean isValid() {
            return "success".equals(status) && data != null && data.result() != null;
        }

        List<PrometheusSeries> getSeries() {
            if (!isValid()) {
                return List.of();
            }
            return data.result();
        }
    }

    record PrometheusData(String resultType, List<PrometheusSeries> result) {}

    record PrometheusSeries(PrometheusMetric metric, List<Object> value) {}

    record PrometheusMetric(String cluster) {}
}
