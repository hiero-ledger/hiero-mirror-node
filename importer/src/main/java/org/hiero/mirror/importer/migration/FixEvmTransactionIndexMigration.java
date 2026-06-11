// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Named
final class FixEvmTransactionIndexMigration extends AsyncJavaMigration<Long> {

    static final long INTERVAL = Duration.ofDays(7).toNanos();

    private static final String V2_PROPERTY = "set citus.max_intermediate_result_size = -1;";

    private static final String NULLIFY_CONTRACT_RESULT_SQL = """
            update contract_result cr
            set transaction_index = null
            where cr.consensus_timestamp >= :consensusStart
              and cr.consensus_timestamp <= :lastConsensusEnd
              and cr.consensus_timestamp in (
                select t.consensus_timestamp
                from transaction t
                where t.consensus_timestamp >= :consensusStart
                  and t.consensus_timestamp <= :lastConsensusEnd
                  and t.type in (7, 8, 50)
              )
            """;

    private static final String NULLIFY_CONTRACT_LOG_SQL = """
            update contract_log cl
            set transaction_index = null
            where cl.consensus_timestamp >= :consensusStart
              and cl.consensus_timestamp <= :lastConsensusEnd
              and cl.consensus_timestamp in (
                select t.consensus_timestamp
                from transaction t
                where t.consensus_timestamp >= :consensusStart
                  and t.consensus_timestamp <= :lastConsensusEnd
                  and t.type in (7, 8, 50)
              )
            """;

    private static final String EVM_INDEX_CTE = """
            with evm_parents as (
                select
                    t.consensus_timestamp,
                    row_number() over (
                        partition by rf.consensus_end
                        order by t.consensus_timestamp
                    ) - 1 as evm_index
                from transaction t
                join record_file rf
                    on t.consensus_timestamp between rf.consensus_start and rf.consensus_end
                where t.consensus_timestamp >= :consensusStart
                  and t.consensus_timestamp <= :lastConsensusEnd
                  and t.type in (7, 8, 50)
                  and (t.nonce = 0 or t.scheduled = true)
            ),
            evm_children as (
                select t.consensus_timestamp, p.evm_index
                from transaction t
                join evm_parents p on t.parent_consensus_timestamp = p.consensus_timestamp
                where t.consensus_timestamp >= :consensusStart
                  and t.consensus_timestamp <= :lastConsensusEnd
                  and t.type in (7, 8, 50)
                  and t.nonce > 0 and t.scheduled = false
            ),
            all_evm as (
                select consensus_timestamp, evm_index from evm_parents
                union all
                select consensus_timestamp, evm_index from evm_children
            )
            """;

    private static final String UPDATE_CONTRACT_RESULT_SQL = EVM_INDEX_CTE + """
            update contract_result cr
            set transaction_index = ae.evm_index
            from all_evm ae
            where cr.consensus_timestamp = ae.consensus_timestamp
              and cr.consensus_timestamp >= :consensusStart
              and cr.consensus_timestamp <= :lastConsensusEnd
            """;

    private static final String UPDATE_CONTRACT_LOG_SQL = EVM_INDEX_CTE + """
            update contract_log cl
            set transaction_index = ae.evm_index
            from all_evm ae
            where cl.consensus_timestamp = ae.consensus_timestamp
              and cl.consensus_timestamp >= :consensusStart
              and cl.consensus_timestamp <= :lastConsensusEnd
            """;

    private static final String SELECT_LAST_TIMESTAMP = """
            select coalesce(
                (select consensus_end from record_file order by consensus_end desc limit 1),
                0
            )
            """;

    private static final String SELECT_RECORD_FILES_RANGE = """
            select consensus_start as min_consensus_timestamp, max_consensus_timestamp
            from record_file
            join (
              select rf.consensus_end
              from record_file as rf
              where rf.consensus_end > :consensusEndLowerBound and rf.consensus_end <= :consensusEndUpperBound
              order by rf.consensus_end desc
              limit 1
            ) as t(max_consensus_timestamp) on true
            where consensus_end > :consensusEndLowerBound and consensus_end <= :consensusEndUpperBound
            order by consensus_end
            limit 1
            """;

    private static final RowMapper<RecordFileSlice> ROW_MAPPER = new DataClassRowMapper<>(RecordFileSlice.class);

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    private final EntityProperties entityProperties;
    private final boolean v2;

    FixEvmTransactionIndexMigration(
            Environment environment,
            DBProperties dbProperties,
            ImporterProperties importerProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider,
            EntityProperties entityProperties) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.entityProperties = entityProperties;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Fix EVM transaction index in contract_result and contract_log to use EVM-only ordering";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.61.1");
    }

    @Override
    protected Long getInitial() {
        final var endTimestamp = getJdbcOperations().queryForObject(SELECT_LAST_TIMESTAMP, Long.class);
        log.info("Starting EVM transaction index fix with initial timestamp: {}.", endTimestamp);
        return endTimestamp;
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long consensusEndTimestamp) {
        final var consensusStartTimestamp = consensusEndTimestamp - INTERVAL;
        final var sliceParams = new MapSqlParameterSource()
                .addValue("consensusEndUpperBound", consensusEndTimestamp)
                .addValue("consensusEndLowerBound", consensusStartTimestamp);
        final var slice = queryForObjectOrNull(SELECT_RECORD_FILES_RANGE, sliceParams, ROW_MAPPER);

        if (slice == null) {
            log.info(
                    "No more record files remaining to process. Last consensus end timestamp: {}.",
                    consensusEndTimestamp);
            return Optional.empty();
        }

        final var params = new MapSqlParameterSource()
                .addValue("consensusStart", slice.minConsensusTimestamp())
                .addValue("lastConsensusEnd", slice.maxConsensusTimestamp());

        getTransactionOperations().executeWithoutResult(status -> {
            if (v2) {
                getJdbcOperations().execute(V2_PROPERTY);
            }
            getNamedParameterJdbcOperations().update(NULLIFY_CONTRACT_RESULT_SQL, params);
            getNamedParameterJdbcOperations().update(NULLIFY_CONTRACT_LOG_SQL, params);
            final var updatedResults = getNamedParameterJdbcOperations().update(UPDATE_CONTRACT_RESULT_SQL, params);
            final var updatedLogs = getNamedParameterJdbcOperations().update(UPDATE_CONTRACT_LOG_SQL, params);
            if (updatedResults > 0 || updatedLogs > 0) {
                log.info(
                        "Fixed EVM transaction index for {} contract_result and {} contract_log rows in range [{}, {}]",
                        updatedResults,
                        updatedLogs,
                        slice.minConsensusTimestamp(),
                        slice.maxConsensusTimestamp());
            }
        });

        return Optional.of(consensusStartTimestamp);
    }

    @Override
    protected boolean performSynchronousSteps() {
        final var persistProperties = entityProperties.getPersist();
        return persistProperties.isContracts() && persistProperties.isContractResults();
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private record RecordFileSlice(long minConsensusTimestamp, long maxConsensusTimestamp) {}
}
