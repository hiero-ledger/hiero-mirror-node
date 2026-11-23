// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Restores the deleted status of contracts undeleted by the synthetic contract updates of the sidecar migration. The
 * revisions from the deleted revision until the next system undelete, if any, are merged into the deleted revision.
 */
@Named
final class FixSidecarMigrationDeletedContractMigration extends ConfigurableJavaMigration {

    static final int BATCH_SIZE = 1000;

    private static final String DELETE_DELETED_HISTORY_SQL = """
            delete from entity_history
            where id = :id and lower(timestamp_range) = :deletedTimestamp
            """;

    private static final String DELETE_SUBSEQUENT_HISTORY_SQL = """
            delete from entity_history
            where id = :id and lower(timestamp_range) > :deletedTimestamp and lower(timestamp_range) < :endTimestamp
            """;

    private static final String EXTEND_DELETED_HISTORY_SQL = """
            update entity_history
            set timestamp_range = int8range(:deletedTimestamp, :endTimestamp)
            where id = :id and lower(timestamp_range) = :deletedTimestamp
            """;

    private static final RowMapper<Undelete> ROW_MAPPER = new DataClassRowMapper<>(Undelete.class);

    private static final String SELECT_SYSTEM_UNDELETES_SQL = """
            select entity_id, consensus_timestamp
            from transaction
            where type = :type and result = :result and entity_id is not null
            """;

    // Not ordered nor paginated since that forces an index scan of the whole table
    private static final String SELECT_UNDELETES_SQL = """
            select h.id, lower(h.timestamp_range) as deleted_timestamp, upper(h.timestamp_range) as undeleted_timestamp
            from entity_history h
            where h.deleted is true
              and h.type = 'CONTRACT'
              and (
                exists (
                  select 1 from entity e
                  where e.id = h.id and e.deleted is false and lower(e.timestamp_range) = upper(h.timestamp_range)
                )
                or exists (
                  select 1 from entity_history n
                  where n.id = h.id and n.deleted is false and lower(n.timestamp_range) = upper(h.timestamp_range)
                )
              )
            """;

    private static final String UPDATE_ENTITY_SQL = """
            update entity
            set deleted = true, timestamp_range = int8range(:deletedTimestamp, null)
            where id = :id and lower(timestamp_range) > :deletedTimestamp
            """;

    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final ObjectProvider<TransactionOperations> transactionOperationsProvider;
    private final boolean v2;

    FixSidecarMigrationDeletedContractMigration(
            final Environment environment,
            final ImporterProperties importerProperties,
            final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            final ObjectProvider<TransactionOperations> transactionOperationsProvider) {
        super(importerProperties.getMigration());
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.transactionOperationsProvider = transactionOperationsProvider;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Restore the deleted status of contracts marked as not deleted by the sidecar migration";
    }

    @Override
    public MigrationVersion getVersion() {
        return v2 ? MigrationVersion.fromVersion("2.37.1") : MigrationVersion.fromVersion("1.132.1");
    }

    @Override
    protected void doMigrate() {
        final var jdbcOperations = jdbcOperationsProvider.getObject();
        final var stopwatch = Stopwatch.createStarted();
        final var undeletes = new ArrayList<>(jdbcOperations.query(SELECT_UNDELETES_SQL, ROW_MAPPER));
        if (undeletes.isEmpty()) {
            log.info("No contracts marked as not deleted by the sidecar migration");
            return;
        }

        undeletes.sort(Comparator.comparingLong(Undelete::id).thenComparingLong(Undelete::deletedTimestamp));
        final var fixes = getFixes(undeletes, getSystemUndeletes(jdbcOperations));
        final var transactionOperations = transactionOperationsProvider.getObject();
        for (final var batch : Lists.partition(fixes, BATCH_SIZE)) {
            // Atomic batches, so a retry never sees a partial fix
            transactionOperations.executeWithoutResult(_ -> fix(jdbcOperations, batch));
        }

        log.info("Restored the deleted status of {} contracts in {}", fixes.size(), stopwatch);
    }

    private static void batchUpdate(
            final NamedParameterJdbcOperations jdbcOperations,
            final List<SqlParameterSource> parameters,
            final String... statements) {
        if (parameters.isEmpty()) {
            return;
        }

        final var batchArgs = parameters.toArray(SqlParameterSource[]::new);
        for (final var statement : statements) {
            jdbcOperations.batchUpdate(statement, batchArgs);
        }
    }

    private static void fix(final NamedParameterJdbcOperations jdbcOperations, final List<Fix> fixes) {
        final var all = new ArrayList<SqlParameterSource>(fixes.size());
        final var bounded = new ArrayList<SqlParameterSource>();
        final var current = new ArrayList<SqlParameterSource>();

        for (final var fix : fixes) {
            final var endTimestamp = fix.endTimestamp();
            final var params = new MapSqlParameterSource()
                    .addValue("deletedTimestamp", fix.deletedTimestamp())
                    .addValue("endTimestamp", endTimestamp != null ? endTimestamp : Long.MAX_VALUE)
                    .addValue("id", fix.id());
            all.add(params);
            (endTimestamp != null ? bounded : current).add(params);
        }

        batchUpdate(jdbcOperations, all, DELETE_SUBSEQUENT_HISTORY_SQL);
        batchUpdate(jdbcOperations, current, UPDATE_ENTITY_SQL, DELETE_DELETED_HISTORY_SQL);
        batchUpdate(jdbcOperations, bounded, EXTEND_DELETED_HISTORY_SQL);
    }

    private static List<Fix> getFixes(final List<Undelete> undeletes, final Map<Long, TreeSet<Long>> systemUndeletes) {
        final var fixes = new ArrayList<Fix>();
        long mergedIntoCurrentId = 0L;
        boolean mergedIntoCurrent = false;

        for (final var undelete : undeletes) {
            final long id = undelete.id();
            if (mergedIntoCurrent && mergedIntoCurrentId == id) {
                // Already merged into the current entity
                continue;
            }

            final var timestamps = systemUndeletes.get(id);
            final var endTimestamp = timestamps != null ? timestamps.higher(undelete.deletedTimestamp()) : null;
            if (endTimestamp != null && endTimestamp == undelete.undeletedTimestamp()) {
                // A legitimate system undelete
                continue;
            }

            fixes.add(new Fix(id, undelete.deletedTimestamp(), endTimestamp));
            mergedIntoCurrent = endTimestamp == null;
            mergedIntoCurrentId = id;
        }

        return fixes;
    }

    private static Map<Long, TreeSet<Long>> getSystemUndeletes(final NamedParameterJdbcOperations jdbcOperations) {
        final var params = new MapSqlParameterSource()
                .addValue("result", ResponseCodeEnum.SUCCESS_VALUE)
                .addValue("type", TransactionType.SYSTEMUNDELETE.getProtoId());
        final var systemUndeletes = new HashMap<Long, TreeSet<Long>>();
        jdbcOperations.query(SELECT_SYSTEM_UNDELETES_SQL, params, (RowCallbackHandler) rs -> systemUndeletes
                .computeIfAbsent(rs.getLong("entity_id"), _ -> new TreeSet<>())
                .add(rs.getLong("consensus_timestamp")));
        return systemUndeletes;
    }

    private record Fix(
            long id, long deletedTimestamp, @Nullable Long endTimestamp) {}

    private record Undelete(long id, long deletedTimestamp, long undeletedTimestamp) {}
}
