// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

class RepeatableMigrationTest {

    private final Context defaultContext = new Context() {
        @Override
        public Configuration getConfiguration() {
            return new FluentConfiguration().baselineVersion("1.0.0").target("100.0.0");
        }

        @Override
        public Connection getConnection() {
            return null;
        }
    };

    @Test
    void checksum() {
        var migrationProperties = new MigrationProperties();
        migrationProperties.setChecksum(2);
        var migration = new TestMigration(Map.of("testMigration", migrationProperties));
        migrate(migration);
        assertThat(migration)
                .returns(2, TestMigration::getChecksum)
                .returns(true, TestMigration::isMigrated)
                .returns(null, TestMigration::getVersion);
    }

    @Test
    void caseInsensitivity() {
        var migrationProperties = new MigrationProperties();
        migrationProperties.setChecksum(4);
        CaseInsensitiveMap<String, MigrationProperties> migrationMap = new CaseInsensitiveMap<>();
        migrationMap.put("TESTMIGRATION", migrationProperties);
        var migration = new TestMigration(migrationMap);
        migrate(migration);
        assertThat(migration)
                .returns(4, TestMigration::getChecksum)
                .returns(true, TestMigration::isMigrated)
                .returns(null, TestMigration::getVersion);
    }

    @Test
    void defaultMigrationProperties() {
        var migration = new TestMigration(Collections.emptyMap());
        migrate(migration);
        assertThat(migration)
                .returns(1, TestMigration::getChecksum)
                .returns(true, TestMigration::isMigrated)
                .returns(null, TestMigration::getVersion);
    }

    @Test
    void disabled() {
        var migrationProperties = new MigrationProperties();
        migrationProperties.setEnabled(false);
        var migration = new TestMigration(Map.of("testMigration", migrationProperties));
        migrate(migration);
        assertThat(migration)
                .returns(1, TestMigration::getChecksum)
                .returns(false, TestMigration::isMigrated)
                .returns(null, TestMigration::getVersion);
    }

    @SneakyThrows
    private void migrate(RepeatableMigration migration) {
        migration.migrate(defaultContext);
    }

    private static class TestMigration extends RepeatableMigration {

        @Getter
        private boolean migrated = false;

        public TestMigration(Map<String, MigrationProperties> migrationPropertiesMap) {
            super(migrationPropertiesMap);
        }

        @Override
        protected void doMigrate() {
            migrated = true;
        }

        @Override
        public String getDescription() {
            return "Test migration";
        }
    }
}
