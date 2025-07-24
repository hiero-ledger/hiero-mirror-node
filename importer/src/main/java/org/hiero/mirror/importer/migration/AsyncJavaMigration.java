// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.BooleanUtils;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.support.TransactionOperations;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

abstract class AsyncJavaMigration<T> extends RepeatableMigration implements Callback {

    private static final String ASYNC_JAVA_MIGRATION_HISTORY_FIXED =
            """
            select exists(select * from flyway_schema_history where version in ('1.109.0', '2.14.0'))
            """;

    private static final String CHECK_FLYWAY_SCHEMA_HISTORY_EXISTENCE_SQL =
            """
            select exists(select * from information_schema.tables
            where table_schema = :schema and table_name = 'flyway_schema_history')
            """;

    private static final String SELECT_LAST_CHECKSUM_SQL =
            """
            select checksum from flyway_schema_history
            where description = :description
            order by installed_rank desc limit 1
            """;

    private static final String SELECT_LAST_CHECKSUM_SQL_PRE_FIX =
            """
            select checksum from flyway_schema_history
            where description = :description and script like 'com.hedera.%'
            order by installed_rank desc limit 1
            """;

    private static final String UPDATE_CHECKSUM_SQL =
            """
            with last as (
              select installed_rank from flyway_schema_history
              where description = :description order by installed_rank desc limit 1
            )
            update flyway_schema_history f
            set checksum = :checksum,
            execution_time = least(2147483647, extract(epoch from now() - f.installed_on) * 1000)
            from last
            where f.installed_rank = last.installed_rank
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final AtomicBoolean complete = new AtomicBoolean(false);
    private final String schema;
    private final AtomicBoolean shouldMigrate = new AtomicBoolean(false);

    protected AsyncJavaMigration(
            Map<String, MigrationProperties> migrationPropertiesMap,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            String schema) {
        super(migrationPropertiesMap);
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.schema = schema;
    }

    protected final JdbcTemplate getJdbcTemplate() {
        return jdbcTemplateProvider.getObject();
    }

    protected final NamedParameterJdbcTemplate getNamedParameterJdbcTemplate() {
        if (namedParameterJdbcTemplate == null) {
            namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(getJdbcTemplate());
        }
        return namedParameterJdbcTemplate;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public String getCallbackName() {
        return getDescription();
    }

    @Override
    public Integer getChecksum() {
        if (!hasFlywaySchemaHistoryTable()) {
            return -1;
        }

        var params = getSqlParamSource();
        var lastChecksum = queryForObjectOrNull(SELECT_LAST_CHECKSUM_SQL, params, Integer.class);
        if (lastChecksum == null) {
            return -1;
        }

        if (!isAsyncJavaMigrationHistoryFixed()) {
            var lastChecksumPreRenaming = queryForObjectOrNull(SELECT_LAST_CHECKSUM_SQL_PRE_FIX, params, Integer.class);
            if ((lastChecksumPreRenaming == null || lastChecksumPreRenaming < 0) && lastChecksum < 0) {
                // when
                // - the asynchronous migration did not complete before being renamed to org.hiero prefix, or did not
                //   run at all
                // - and the runs after renaming didn't complete either
                // subtract the checksum by 1 as the return value so the migration continues
                return lastChecksum - 1;
            }

            // migration history isn't fixed, return the same checksum so flyway will skip it
            return lastChecksum;
        }

        if (lastChecksum < 0) {
            return lastChecksum - 1;
        } else if (lastChecksum != getSuccessChecksum()) {
            return -1;
        }

        return lastChecksum;
    }

    @Override
    public void handle(Event event, Context context) {
        if (event != Event.AFTER_MIGRATE_OPERATION_FINISH || !shouldMigrate.get()) {
            // Checking event type as a safeguard even though flyway should only call handle() with
            // AFTER_MIGRATE_OPERATION_FINISH event since it's the only event this callback supports.
            return;
        }

        if (!performSynchronousSteps()) {
            onSuccess();
            return;
        }

        runMigrateAsync();
    }

    public <O> O queryForObjectOrNull(String sql, SqlParameterSource paramSource, Class<O> requiredType) {
        try {
            return getNamedParameterJdbcTemplate().queryForObject(sql, paramSource, requiredType);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE_OPERATION_FINISH;
    }

    boolean isComplete() {
        return complete.get();
    }

    @Override
    protected void doMigrate() throws IOException {
        int checksum = getSuccessChecksum();
        if (checksum <= 0) {
            throw new IllegalArgumentException(String.format("Invalid non-positive success checksum %d", checksum));
        }

        shouldMigrate.set(true);
    }

    protected abstract T getInitial();

    /**
     * Gets the success checksum to set for the migration in flyway schema history table. Note the checksum is required
     * to be positive.
     *
     * @return The success checksum for the migration
     */
    protected final int getSuccessChecksum() {
        return migrationProperties.getChecksum();
    }

    protected abstract TransactionOperations getTransactionOperations();

    protected void migrateAsync() {
        log.info("Starting asynchronous migration");

        long count = 0;
        var stopwatch = Stopwatch.createStarted();
        var last = Optional.of(getInitial());
        long minutes = 1L;

        try {
            do {
                final var previous = last;
                last = Objects.requireNonNullElse(
                        getTransactionOperations().execute(t -> migratePartial(previous.get())), Optional.empty());
                count++;

                long elapsed = stopwatch.elapsed(TimeUnit.MINUTES);
                if (elapsed >= minutes) {
                    log.info("Completed iteration {} with last value: {}", count, last.orElse(null));
                    minutes = elapsed + 1;
                }
            } while (last.isPresent());

            log.info("Successfully completed asynchronous migration with {} iterations in {}", count, stopwatch);
        } catch (Exception e) {
            log.error("Error executing asynchronous migration after {} iterations in {}", count, stopwatch);
            throw e;
        }
    }

    @Nonnull
    protected abstract Optional<T> migratePartial(T last);

    /**
     * Perform any synchronous portion of the migration
     *
     * @return boolean indicating if async migration should be performed
     */
    protected boolean performSynchronousSteps() {
        return true;
    }

    protected final void runMigrateAsync() {
        Mono.fromRunnable(this::migrateAsync)
                .subscribeOn(Schedulers.single())
                .doOnSuccess(t -> onSuccess())
                .doOnError(t -> log.error("Asynchronous migration failed:", t))
                .doFinally(s -> complete.set(true))
                .subscribe();
    }

    private MapSqlParameterSource getSqlParamSource() {
        return new MapSqlParameterSource().addValue("description", getDescription());
    }

    private boolean hasFlywaySchemaHistoryTable() {
        var exists = getNamedParameterJdbcTemplate().queryForObject(
                CHECK_FLYWAY_SCHEMA_HISTORY_EXISTENCE_SQL, Map.of("schema", schema), Boolean.class);
        return BooleanUtils.isTrue(exists);
    }

    private boolean isAsyncJavaMigrationHistoryFixed() {
        var fixed = getNamedParameterJdbcTemplate()
                .getJdbcTemplate()
                .queryForObject(ASYNC_JAVA_MIGRATION_HISTORY_FIXED, Boolean.class);
        return BooleanUtils.isTrue(fixed);
    }

    private void onSuccess() {
        var paramSource = getSqlParamSource().addValue("checksum", getSuccessChecksum());
        getNamedParameterJdbcTemplate().update(UPDATE_CHECKSUM_SQL, paramSource);
    }

    @VisibleForTesting
    void setComplete(boolean complete) {
        this.complete.set(complete);
    }
}
