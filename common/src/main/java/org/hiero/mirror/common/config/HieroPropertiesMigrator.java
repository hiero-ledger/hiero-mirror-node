// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Function;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Migrates Hedera properties to Hiero to support both properties at the same time for backwards compatibility purposes.
 * Searches all property sources for 'hedera' prefixed properties and inserts a higher priority property source with the
 * equivalent 'hiero' prefixed properties. This works with both 'hedera.mirror' style properties and 'HEDERA_MIRROR'
 * style environment variables. Has to be registered as a listener in spring.factories to work properly.
 */
@CustomLog
public class HieroPropertiesMigrator implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        var propertySources = event.getEnvironment().getPropertySources();

        for (var propertySource : propertySources) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerableSource) {
                migrate(enumerableSource).ifPresent(p -> propertySources.addAfter(propertySource.getName(), p));
            }
        }
    }

    private Optional<EnumerablePropertySource<?>> migrate(EnumerablePropertySource<?> propertySource) {
        var properties = new LinkedHashMap<String, Object>();
        boolean environment = propertySource instanceof SystemEnvironmentPropertySource;
        Function<String, String> replacer = environment ? this::convertEnvironmentVariable : this::convertPropertyName;

        for (var name : propertySource.getPropertyNames()) {
            if (StringUtils.startsWithIgnoreCase(name, "hedera")) {
                var value = propertySource.getProperty(name);
                var migratedName = replacer.apply(name);
                properties.put(migratedName, value);
            }
        }

        if (!properties.isEmpty()) {
            var sourceName = propertySource.getName() + "-hiero";
            var migratedPropertySource = environment
                    ? new SystemEnvironmentPropertySource(sourceName, properties)
                    : new MapPropertySource(sourceName, properties);
            log.warn("Deprecated 'hedera' properties automatically migrated to 'hiero': {}", properties.keySet());
            return Optional.of(migratedPropertySource);
        }

        return Optional.empty();
    }

    private String convertPropertyName(String name) {
        return StringUtils.replaceIgnoreCase(name, "hedera.", "hiero.", 1);
    }

    private String convertEnvironmentVariable(String name) {
        return StringUtils.replaceIgnoreCase(name, "HEDERA_", "HIERO_", 1);
    }
}
