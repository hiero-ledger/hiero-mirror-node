// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterProperties;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
class HealthCheckConfiguration {
    private final ImporterProperties importerProperties;
    private final Collection<ParserProperties> parserProperties;

    @Bean
    CompositeHealthContributor streamFileActivity(MeterRegistry meterRegistry) {
        var registry = getRegistry(meterRegistry);
        Map<String, HealthIndicator> healthIndicators = parserProperties.stream()
                .collect(Collectors.toMap(
                        k -> k.getStreamType().toString(),
                        v -> new StreamFileHealthIndicator(registry, importerProperties, v)));

        return CompositeHealthContributor.fromMap(healthIndicators);
    }

    private MeterRegistry getRegistry(MeterRegistry meterRegistry) {
        if (meterRegistry instanceof CompositeMeterRegistry composite) {
            for (var registry : composite.getRegistries()) {
                if (registry instanceof PrometheusMeterRegistry) {
                    return registry;
                }
            }
        }
        return meterRegistry;
    }
}
