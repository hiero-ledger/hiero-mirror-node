// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class AbstractJavaMigrationTest {

    @ParameterizedTest
    @MethodSource("skipMigrationTestArgumentProvider")
    void shouldSkipTheCorrectMigrations(JavaMigration javaMigration, Configuration configuration, boolean shouldSkip) {
        assertThat(javaMigration.skipMigration(configuration)).isEqualTo(shouldSkip);
    }

    @DisplayName("Verify migration")
    @ParameterizedTest(name = "with version {0}, baseline {1} and target {2} produces {3}")
    @CsvSource({
        "0.0.5, 0, 0.0.4, false",
        "0.0.5, 1, 2, false",
        "0.0.5, 0, 2, true",
        "0.0.5, 0, latest, true",
        "1.0.0, , 2, true",
        "1.11.6, 1, 2, true",
        "1.11.6, 1.999.999, 2.999.999, false",
        "2.0.0, 1, 1.999.999, false",
        ", , 2, true",
        ", 1, 2, true"
    })
    void verify(String version, String baseline, String target, boolean result) throws IOException {
        AbstractJavaMigrationSpy migration = new AbstractJavaMigrationSpy(version);
        migration.migrate(new FlywayContext(getConfiguration(baseline, target)));
        assertThat(migration.isMigrationCompleted()).isEqualTo(result);
    }

    private static ClassicConfiguration getConfiguration(String baseLine, String target) {
        ClassicConfiguration configuration = new ClassicConfiguration();
        configuration.setBaselineVersionAsString(baseLine);
        if (target != null) {
            configuration.setTargetAsString(target);
        }
        return configuration;
    }

    private static Stream<Arguments> skipMigrationTestArgumentProvider() {
        return Stream.of(
                // Repeatable migration with no minimum version is never skipped
                Arguments.of(new JavaMigration(), getConfiguration(null, null), false),
                Arguments.of(new JavaMigration(), getConfiguration("1.57.1", null), false),
                Arguments.of(new JavaMigration(), getConfiguration("1.57.1", "1.58.5"), false),
                Arguments.of(new JavaMigration(), getConfiguration("1.57.1", "1.58.4"), false),
                Arguments.of(new JavaMigration(), getConfiguration("1.58.4", "1.57.1"), false),

                // Repeatable migration with minimum version can be skipped in some cases
                Arguments.of(
                        new JavaMigration(null, MigrationVersion.fromVersion("1.58.5")),
                        getConfiguration("1.57.1", "1.58.4"),
                        true),

                // Repeatable migration with minimum version is not skipped,
                // when the target is greater than the minimum required version, or
                // when the target is null
                Arguments.of(
                        new JavaMigration(null, MigrationVersion.fromVersion("1.58.5")),
                        getConfiguration("1.57.1", null),
                        false),
                Arguments.of(
                        new JavaMigration(null, MigrationVersion.fromVersion("1.58.5")),
                        getConfiguration("1.57.1", "1.58.6"),
                        false),

                // Non-repeatable migrations are skipped,
                // if the baseline is greater than their version, or
                // if the version is greater than the target
                Arguments.of(
                        new JavaMigration(
                                MigrationVersion.fromVersion("1.58.4"), MigrationVersion.fromVersion("1.58.6")),
                        getConfiguration("1.58.5", "1.59.0"),
                        true),
                Arguments.of(
                        new JavaMigration(
                                MigrationVersion.fromVersion("1.58.5"), MigrationVersion.fromVersion("1.57.1")),
                        getConfiguration("1.55.0", "1.58.0"),
                        true),

                // Non-repeatable migrations are not skipped,
                // if the target is null, or
                // if the their version is lower than the target version.
                Arguments.of(
                        new JavaMigration(
                                MigrationVersion.fromVersion("1.58.4"), MigrationVersion.fromVersion("1.58.3")),
                        getConfiguration("1.55.0", "1.58.5"),
                        false),
                Arguments.of(
                        new JavaMigration(
                                MigrationVersion.fromVersion("1.58.4"), MigrationVersion.fromVersion("1.58.3")),
                        getConfiguration("1.55.0", null),
                        false));
    }

    @Data
    @RequiredArgsConstructor
    private class AbstractJavaMigrationSpy extends AbstractJavaMigration {

        private boolean migrationCompleted = false;
        private final String version;

        @Override
        public void doMigrate() {
            migrationCompleted = true;
        }

        @Override
        public MigrationVersion getVersion() {
            return version != null ? MigrationVersion.fromVersion(version) : null;
        }

        @Override
        public String getDescription() {
            return getClass().getSimpleName();
        }
    }

    @AllArgsConstructor
    private class FlywayContext implements Context {
        private final ClassicConfiguration configuration;

        @Override
        public Configuration getConfiguration() {
            return configuration;
        }

        @Override
        public Connection getConnection() {
            return null;
        }
    }

    private static class JavaMigration extends AbstractJavaMigration {

        private MigrationVersion version;

        private MigrationVersion minimumVersion;

        public JavaMigration() {
            this(null, null);
        }

        public JavaMigration(MigrationVersion version) {
            this(version, null);
        }

        public JavaMigration(MigrationVersion version, MigrationVersion minimumVersion) {
            this.version = version;
            this.minimumVersion = minimumVersion;
        }

        @Override
        protected void doMigrate() {
            throw new UnsupportedOperationException("Missing implementation");
        }

        @Override
        public MigrationVersion getVersion() {
            return version;
        }

        @Override
        public MigrationVersion getMinimumVersion() {
            return minimumVersion;
        }

        @Override
        public String getDescription() {
            return "Repetable java migration";
        }
    }
}
