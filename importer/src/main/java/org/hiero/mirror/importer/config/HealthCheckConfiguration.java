// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Collection;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.parser.ParserProperties;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class HealthCheckConfiguration {
    private final ImporterProperties importerProperties;
    private final Collection<ParserProperties> parserProperties;

    @Bean
    HealthStatusResolver healthStatusResolver(HealthEndpoint healthEndpoint) {
        return group -> {
            final var descriptor = healthEndpoint.healthForPath(group);
            return descriptor != null ? descriptor.getStatus() : Status.DOWN;
        };
    }

    @Bean
    CompositeHealthContributor streamFileActivity(MeterRegistry meterRegistry) {
        final var registry = getRegistry(meterRegistry);
        final var healthIndicators = parserProperties.stream()
                .collect(Collectors.toMap(
                        k -> k.getStreamType().toString(),
                        v -> new StreamFileHealthIndicator(registry, importerProperties, v)));

        return CompositeHealthContributor.fromMap(healthIndicators);
    }

    private MeterRegistry getRegistry(MeterRegistry meterRegistry) {
        if (meterRegistry instanceof CompositeMeterRegistry composite) {
            for (final var registry : composite.getRegistries()) {
                if (registry instanceof PrometheusMeterRegistry) {
                    return registry;
                }
            }
        }
        return meterRegistry;
    }
}
