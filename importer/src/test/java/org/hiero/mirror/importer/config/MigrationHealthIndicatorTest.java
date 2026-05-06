// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class MigrationHealthIndicatorTest {

    private final MigrationHealthIndicator migrationHealthIndicator = new MigrationHealthIndicator();

    @Test
    void healthDownWhenMigrationsRunning() {
        assertThat(migrationHealthIndicator.health())
                .isNotNull()
                .extracting(Health::getStatus)
                .isEqualTo(Status.DOWN);
    }

    @Test
    void healthUpWhenMigrationsComplete() {
        migrationHealthIndicator.handle(Event.AFTER_MIGRATE, null);
        assertThat(migrationHealthIndicator.health())
                .isNotNull()
                .extracting(Health::getStatus)
                .isEqualTo(Status.UP);
    }

    @Test
    void supports() {
        assertThat(migrationHealthIndicator.supports(null, null)).isFalse();
        assertThat(migrationHealthIndicator.supports(Event.AFTER_BASELINE, null))
                .isFalse();
        assertThat(migrationHealthIndicator.supports(Event.AFTER_MIGRATE, null)).isTrue();
    }
}
