// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.configuration.Configuration;

abstract class ConfigurableJavaMigration extends AbstractJavaMigration {

    private static final String ASYNC = "async";
    private static final MigrationProperties DEFAULT_MIGRATION_PROPERTIES = new MigrationProperties();

    protected final MigrationProperties migrationProperties;

    protected ConfigurableJavaMigration(Map<String, MigrationProperties> migrationPropertiesMap) {
        String propertiesKey = StringUtils.uncapitalize(getClass().getSimpleName());
        var defaultProperties = DEFAULT_MIGRATION_PROPERTIES;
        if (this instanceof AsyncJavaMigration<?>) {
            defaultProperties = migrationPropertiesMap.getOrDefault(ASYNC, DEFAULT_MIGRATION_PROPERTIES);
        }
        migrationProperties = migrationPropertiesMap.getOrDefault(propertiesKey, defaultProperties);
    }

    @Override
    protected boolean skipMigration(Configuration configuration) {
        if (!migrationProperties.isEnabled()) {
            log.info("Skip migration since it's disabled");
            return true;
        }

        return super.skipMigration(configuration);
    }
}
