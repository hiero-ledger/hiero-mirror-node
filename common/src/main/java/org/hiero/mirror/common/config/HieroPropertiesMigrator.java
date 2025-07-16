// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Function;
import lombok.CustomLog;
import org.apache.commons.lang3.Strings;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Migrates Hedera properties to Hiero to support both properties at the same time for backwards compatibility purposes.
 * Searches all property sources for 'hedera' prefixed properties and inserts a higher priority property source with the
 * equivalent 'hiero' prefixed properties. This works with both 'hedera.mirror' style properties and 'HEDERA_MIRROR'
 * style environment variables. Has to be registered as a listener in spring.factories to work properly.
 */
@CustomLog
public class HieroPropertiesMigrator implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final String DEFAULT_PROPERTY_SOURCE_NAME_SUFFIX = "-hiero";

    // For a SystemEnvironmentPropertySource, only when its name is "systemEnvironment" or ends with
    // "-systemEnvironment", spring will use `SystemEnvironmentPropertyMapper` to map the environment variables
    // and bind the properties to configuration property beans
    private static final String SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME_SUFFIX =
            "-hiero-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

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
            if (Strings.CI.startsWith(name, "hedera")) {
                var value = propertySource.getProperty(name);
                var migratedName = replacer.apply(name);
                properties.put(migratedName, value);
            }
        }

        if (!properties.isEmpty()) {
            var sourceName = propertySource.getName()
                    + (environment
                            ? SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME_SUFFIX
                            : DEFAULT_PROPERTY_SOURCE_NAME_SUFFIX);
            var migratedPropertySource = environment
                    ? new SystemEnvironmentPropertySource(sourceName, properties)
                    : new MapPropertySource(sourceName, properties);
            log.warn("Deprecated 'hedera' properties automatically migrated to 'hiero': {}", properties.keySet());
            return Optional.of(migratedPropertySource);
        }

        return Optional.empty();
    }

    private String convertPropertyName(String name) {
        return Strings.CI.replace(name, "hedera.", "hiero.", 1);
    }

    private String convertEnvironmentVariable(String name) {
        return Strings.CI.replace(name, "HEDERA_", "HIERO_", 1);
    }
}
