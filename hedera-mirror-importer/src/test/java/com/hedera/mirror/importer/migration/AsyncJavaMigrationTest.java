// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.Uninterruptibles;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.db.DBProperties;
import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
class AsyncJavaMigrationTest extends ImporterIntegrationTest {

    private static final int ELAPSED = 20;
    private static final String TEST_MIGRATION_DESCRIPTION = "Async java migration for testing";

    private final DBProperties dbProperties;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final TransactionOperations transactionOperations;
    private final String script = TestAsyncJavaMigration.class.getName();
    private final Collection<AsyncJavaMigration<?>> asyncMigrations;

    @AfterEach
    @BeforeEach
    void cleanup() {
        namedParameterJdbcTemplate.update(
                "delete from flyway_schema_history where script = :script", Map.of("script", script));
    }

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
        addMigrationHistory(new MigrationHistory(existing, ELAPSED, 1000));
        var migration = new TestAsyncJavaMigration(false, new MigrationProperties(), 1);
        assertThat(migration.getChecksum()).isEqualTo(expected);
    }

    @Test
    void migrate() throws Exception {
        addMigrationHistory(new MigrationHistory(-1, ELAPSED, 1000));
        addMigrationHistory(new MigrationHistory(-2, ELAPSED, 1001));
        var migration = new TestAsyncJavaMigration(false, new MigrationProperties(), 1);
        migrateSync(migration);
        assertThat(getAllMigrationHistory())
                .hasSize(2)
                .extracting(MigrationHistory::getChecksum)
                .containsExactly(-1, 1);
    }

    @Test
    void migrateUpdatedExecutionTime() throws Exception {
        addMigrationHistory(new MigrationHistory(-1, ELAPSED, 1000));
        var migration = new TestAsyncJavaMigration(false, new MigrationProperties(), 1);
        migrateSync(migration);
        assertThat(getAllMigrationHistory())
                .hasSize(1)
                .extracting(MigrationHistory::getExecutionTime)
                .isNotEqualTo(ELAPSED);
    }

    @Test
    void migrateError() throws Exception {
        addMigrationHistory(new MigrationHistory(-1, ELAPSED, 1000));
        addMigrationHistory(new MigrationHistory(-2, ELAPSED, 1001));
        var migration = new TestAsyncJavaMigration(true, new MigrationProperties(), 0);
        migrateSync(migration);
        assertThat(getAllMigrationHistory())
                .hasSize(2)
                .extracting(MigrationHistory::getChecksum)
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

    private void addMigrationHistory(MigrationHistory migrationHistory) {
        if (migrationHistory.getChecksum() == null) {
            return;
        }

        var paramSource = new MapSqlParameterSource()
                .addValue("installedRank", migrationHistory.getInstalledRank())
                .addValue("description", TEST_MIGRATION_DESCRIPTION)
                .addValue("script", script)
                .addValue("checksum", migrationHistory.getChecksum());
        var sql =
                """
                insert into flyway_schema_history (installed_rank, description, type, script, checksum,
                installed_by, execution_time, success) values (:installedRank, :description, 'JDBC', :script,
                :checksum, 20, 100, true)
                """;
        namedParameterJdbcTemplate.update(sql, paramSource);
    }

    private List<MigrationHistory> getAllMigrationHistory() {
        return namedParameterJdbcTemplate.query(
                "select installed_rank, checksum, execution_time from flyway_schema_history where "
                        + "script = :script order by installed_rank asc",
                Map.of("script", script),
                (rs, rowNum) -> {
                    Integer checksum = rs.getInt("checksum");
                    int executionTime = rs.getInt("execution_time");
                    int installedRank = rs.getInt("installed_rank");
                    return new MigrationHistory(checksum, executionTime, installedRank);
                });
    }

    private void migrateSync(AsyncJavaMigration<?> migration) throws Exception {
        migration.doMigrate();

        while (!migration.isComplete()) {
            Uninterruptibles.sleepUninterruptibly(100L, TimeUnit.MILLISECONDS);
        }
    }

    @Value
    private static class MigrationHistory {
        private Integer checksum;
        private int executionTime;
        private int installedRank;
    }

    @Value
    private class TestAsyncJavaMigration extends AsyncJavaMigration<Long> {

        private final boolean error;
        private final long sleep;

        public TestAsyncJavaMigration(boolean error, MigrationProperties migrationProperties, long sleep) {
            super(
                    Map.of("testAsyncJavaMigration", migrationProperties),
                    AsyncJavaMigrationTest.this.namedParameterJdbcTemplate,
                    dbProperties.getSchema());
            this.error = error;
            this.sleep = sleep;
        }

        @Override
        public String getDescription() {
            return TEST_MIGRATION_DESCRIPTION;
        }

        @Nonnull
        @Override
        protected Optional<Long> migratePartial(final Long last) {
            if (sleep > 0) {
                Uninterruptibles.sleepUninterruptibly(sleep, TimeUnit.SECONDS);
            }

            if (error) {
                throw new RuntimeException();
            }

            return Optional.empty();
        }

        @Override
        protected TransactionOperations getTransactionOperations() {
            return transactionOperations;
        }

        @Override
        protected Long getInitial() {
            return Long.MAX_VALUE;
        }
    }
}
