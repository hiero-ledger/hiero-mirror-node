// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.util.concurrent.Uninterruptibles;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.Value;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.db.DBProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

class AsyncJavaMigrationBaseTest extends ImporterIntegrationTest {

    protected static final int ELAPSED = 20;
    protected static final String TEST_MIGRATION_DESCRIPTION = "Async java migration for testing";

    @Resource
    protected DBProperties dbProperties;

    @Resource
    protected NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    protected TransactionOperations transactionOperations;

    private final String script = AsyncJavaMigrationBaseTest.TestAsyncJavaMigration.class.getName();

    @AfterEach
    @BeforeEach
    void cleanup() {
        ownerJdbcTemplate.update("delete from flyway_schema_history where description = ?", TEST_MIGRATION_DESCRIPTION);
    }

    protected void addMigrationHistory(
            AsyncJavaMigrationBaseTest.MigrationHistory migrationHistory, Function<String, String> scriptCustomizer) {
        if (migrationHistory.getChecksum() == null) {
            return;
        }

        var paramSource = new MapSqlParameterSource()
                .addValue("installedRank", migrationHistory.getInstalledRank())
                .addValue("description", TEST_MIGRATION_DESCRIPTION)
                .addValue("script", scriptCustomizer.apply(script))
                .addValue("checksum", migrationHistory.getChecksum());
        var sql =
                """
                insert into flyway_schema_history (installed_rank, description, type, script, checksum,
                installed_by, execution_time, success) values (:installedRank, :description, 'JDBC', :script,
                :checksum, 20, 100, true)
                """;
        namedParameterJdbcTemplate.update(sql, paramSource);
    }

    protected void addMigrationHistory(AsyncJavaMigrationBaseTest.MigrationHistory migrationHistory) {
        addMigrationHistory(migrationHistory, Function.identity());
    }

    protected List<AsyncJavaMigrationBaseTest.MigrationHistory> getAllMigrationHistory() {
        return namedParameterJdbcTemplate.query(
                """
                        select installed_rank, checksum, execution_time
                        from flyway_schema_history
                        where description = :description
                        order by installed_rank asc""",
                Map.of("description", TEST_MIGRATION_DESCRIPTION),
                (rs, rowNum) -> {
                    Integer checksum = rs.getInt("checksum");
                    int executionTime = rs.getInt("execution_time");
                    int installedRank = rs.getInt("installed_rank");
                    return new AsyncJavaMigrationBaseTest.MigrationHistory(checksum, executionTime, installedRank);
                });
    }

    @Value
    protected static class MigrationHistory {
        private Integer checksum;
        private int executionTime;
        private int installedRank;
    }

    @Value
    protected class TestAsyncJavaMigration extends AsyncJavaMigration<Long> {

        private final boolean error;
        private final long sleep;

        public TestAsyncJavaMigration(boolean error, MigrationProperties migrationProperties, long sleep) {
            super(
                    Map.of("testAsyncJavaMigration", migrationProperties),
                    AsyncJavaMigrationBaseTest.this.namedParameterJdbcTemplate,
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
