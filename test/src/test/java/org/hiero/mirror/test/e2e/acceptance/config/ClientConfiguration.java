// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.config;

import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@Configuration
@EnableRetry
@RequiredArgsConstructor
class ClientConfiguration {

    private final AcceptanceTestProperties acceptanceTestProperties;

    @Bean
    RestClientCustomizer restClientCustomizer() {
        var baseUrl = acceptanceTestProperties.getRestProperties().getBaseUrl();
        var clientProperties = acceptanceTestProperties.getWebClientProperties();
        var factorySettings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(clientProperties.getConnectionTimeout())
                .withReadTimeout(clientProperties.getReadTimeout());
        var logger = LoggerFactory.getLogger(MirrorNodeClient.class);

        return builder -> builder.baseUrl(baseUrl)
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setAccept((List.of(MediaType.APPLICATION_JSON)));
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setCacheControl(CacheControl.noStore());
                })
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(factorySettings))
                .requestInterceptor((request, body, execution) -> {
                    var response = execution.execute(request, body);
                    var statusCode = response.getStatusCode();

                    if (logger.isDebugEnabled() || statusCode != HttpStatus.NOT_FOUND) {
                        logger.info("{} {}: {}", request.getMethod(), request.getURI(), statusCode);
                    }

                    return response;
                });
    }

    @Bean
    RetryTemplate retryTemplate() {
        List<Class<? extends Throwable>> retryableExceptions = List.of(
                PrecheckStatusException.class,
                ReceiptStatusException.class,
                RuntimeException.class,
                TimeoutException.class,
                HttpClientErrorException.class,
                ResourceAccessException.class);
        return RetryTemplate.builder()
                .fixedBackoff(acceptanceTestProperties.getBackOffPeriod().toMillis())
                .maxAttempts(acceptanceTestProperties.getMaxRetries())
                .retryOn(retryableExceptions)
                .traversingCauses()
                .build();
    }
}
