// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import java.util.Map;
import org.flywaydb.core.api.MigrationVersion;

abstract class RepeatableMigration extends ConfigurableJavaMigration {

    protected RepeatableMigration(Map<String, MigrationProperties> migrationPropertiesMap) {
        super(migrationPropertiesMap);
    }

    @Override
    public Integer getChecksum() {
        return migrationProperties.getChecksum();
    }

    @Override
    public final MigrationVersion getVersion() {
        return null; // Repeatable migration
    }
}
