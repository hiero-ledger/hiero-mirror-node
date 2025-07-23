// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.Uninterruptibles;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.callback.SimpleContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
@Tag("migration")
class AsyncJavaMigrationTest extends AsyncJavaMigrationBaseTest {

    private final Collection<AsyncJavaMigration<?>> asyncMigrations;

    @Test
    void disabledInConfig() {
        asyncMigrations.forEach(migration -> {
            assertThat(migration.migrationProperties.isEnabled())
                    .as("%s is not disabled", migration.getClass().getSimpleName())
                    .isFalse();
        });
    }

    @ParameterizedTest
    @CsvSource(value = {", -1", "-1, -2", "1, 1", "2, -1"})
    void getChecksum(Integer existing, Integer expected) {
        addMigrationHistory(new MigrationHistory(existing, ELAPSED, 1000, SCRIPT));
        var migration = new TestAsyncJavaMigration(false, new MigrationProperties(), 1);
        assertThat(migration.getChecksum()).isEqualTo(expected);
    }

    @Test
    void getCallbackName() {
        var expected =
                asyncMigrations.stream().map(AsyncJavaMigration::getDescription).toList();
        assertThat(asyncMigrations.stream()
                        .map(AsyncJavaMigration::getCallbackName)
                        .toList())
                .containsExactlyElementsOf(expected);
    }

    @Test
    void migrate() throws Exception {
        addMigrationHistory(new MigrationHistory(-1, ELAPSED, 1000, SCRIPT));
        addMigrationHistory(new MigrationHistory(-2, ELAPSED, 1001, SCRIPT));
        var migration = new TestAsyncJavaMigration(false, new MigrationProperties(), 1);
        migrateSync(migration);
        assertThat(getAllMigrationHistory())
                .hasSize(2)
                .extracting(MigrationHistory::checksum)
                .containsExactly(-1, 1);
    }

    @Test
    void migrateUpdatedExecutionTime() throws Exception {
        addMigrationHistory(new MigrationHistory(-1, ELAPSED, 1000, SCRIPT));
        var migration = new TestAsyncJavaMigration(false, new MigrationProperties(), 1);
        migrateSync(migration);
        assertThat(getAllMigrationHistory())
                .hasSize(1)
                .extracting(AsyncJavaMigrationBaseTest.MigrationHistory::executionTime)
                .isNotEqualTo(ELAPSED);
    }

    @Test
    void migrateError() throws Exception {
        addMigrationHistory(new AsyncJavaMigrationBaseTest.MigrationHistory(-1, ELAPSED, 1000, SCRIPT));
        addMigrationHistory(new AsyncJavaMigrationBaseTest.MigrationHistory(-2, ELAPSED, 1001, SCRIPT));
        var migration = new TestAsyncJavaMigration(true, new MigrationProperties(), 0);
        migrateSync(migration);
        assertThat(getAllMigrationHistory())
                .hasSize(2)
                .extracting(AsyncJavaMigrationBaseTest.MigrationHistory::checksum)
                .containsExactly(-1, -2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void migrateNonPositiveSuccessChecksum(int checksum) {
        var migrationProperties = new MigrationProperties();
        migrationProperties.setChecksum(checksum);
        var migration = new TestAsyncJavaMigration(false, migrationProperties, 0);
        assertThatThrownBy(migration::doMigrate).isInstanceOf(IllegalArgumentException.class);
        assertThat(getAllMigrationHistory()).isEmpty();
    }

    private void migrateSync(AsyncJavaMigration<?> migration) throws Exception {
        migration.doMigrate();
        migration.handle(Event.AFTER_MIGRATE_OPERATION_FINISH, new SimpleContext(new FluentConfiguration()));

        while (!migration.isComplete()) {
            Uninterruptibles.sleepUninterruptibly(100L, TimeUnit.MILLISECONDS);
        }
    }
}
