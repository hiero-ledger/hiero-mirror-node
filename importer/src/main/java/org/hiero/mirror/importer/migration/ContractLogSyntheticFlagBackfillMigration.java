// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.convert.DurationStyle;
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
final class ContractLogSyntheticFlagBackfillMigration extends AsyncJavaMigration<Long> {

    static final String DEFAULT_BATCH_INTERVAL = "3h";

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";

    private static final String TRANSFER_SIGNATURE_HEX =
            "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    private static final String APPROVE_SIGNATURE_HEX =
            "8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925";
    private static final String APPROVE_FOR_ALL_SIGNATURE_HEX =
            "17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31";

    private static final String CREATE_PROGRESS_TABLE = """
            create table if not exists contract_log_synthetic_flag_progress_temp(
                upper_bound bigint not null
            );
            """;

    private static final String DROP_PROGRESS_TABLE = """
            drop table if exists contract_log_synthetic_flag_progress_temp;
            """;

    private static final String SELECT_MAX_CONSENSUS_END = "select max(consensus_end) from record_file";

    private static final String SELECT_PROGRESS_UPPER_BOUND =
            "select (select upper_bound from contract_log_synthetic_flag_progress_temp limit 1)";

    private static final String CHECKPOINT_SQL = """
            with clear_table as (delete from contract_log_synthetic_flag_progress_temp)
            insert into contract_log_synthetic_flag_progress_temp(upper_bound)
            values (:upperBound)
            """;

    private static final String SELECT_RECORD_FILES_RANGE = """
            select
                (select consensus_start from record_file
                    where consensus_end between :consensusEndLowerBound and :consensusEndUpperBound
                    order by consensus_end limit 1) as min_consensus_timestamp,
                (select consensus_end from record_file
                    where consensus_end between :consensusEndLowerBound and :consensusEndUpperBound
                    order by consensus_end desc limit 1) as max_consensus_timestamp
            """;

    // Only rows with no matching contract_result are touched, since real EVM-native logs of the same signatures
    // always have one.
    private static final String BACKFILL_SYNTHETIC_SQL = """
            update contract_log
            set synthetic = true
            where synthetic is not true
              and topic0 in (
                decode(:transferSignature, 'hex'),
                decode(:approveSignature, 'hex'),
                decode(:approveForAllSignature, 'hex')
              )
              and consensus_timestamp >= :consensusStart
              and consensus_timestamp <= :lastConsensusEnd
              and consensus_timestamp not in (
                select consensus_timestamp from contract_result
                where consensus_timestamp >= :consensusStart
                  and consensus_timestamp <= :lastConsensusEnd
              )
            """;

    // Newly-flagged rows were missing from FixEvmTransactionIndexMigration's EVM slot count, so every real
    // transaction ordered after one in the same block needs its transaction_index recomputed here too.
    private static final String RECOMPUTE_EVM_TRANSACTION_INDEX_SQL = """
            with evm_candidates as (
                select
                    cr.consensus_timestamp,
                    (cr.transaction_nonce = 0 or cr.contract_id = :hookContractId) as is_root
                from contract_result cr
                where cr.consensus_timestamp >= :consensusStart
                  and cr.consensus_timestamp <= :lastConsensusEnd
                  and cr.transaction_result <> 312
                union all
                select distinct
                    cl.consensus_timestamp,
                    true as is_root
                from contract_log cl
                where cl.synthetic = true
                  and cl.consensus_timestamp >= :consensusStart
                  and cl.consensus_timestamp <= :lastConsensusEnd
            ),
            evm_index as (
                select
                    ec.consensus_timestamp,
                    sum(case when ec.is_root then 1 else 0 end) over (
                        partition by rf.consensus_end
                        order by ec.consensus_timestamp
                    ) - 1 as evm_index
                from evm_candidates ec
                join record_file rf
                    on ec.consensus_timestamp between rf.consensus_start and rf.consensus_end
                where rf.consensus_end between :consensusStart and :lastConsensusEnd
            ),
            updated_contract_result as (
                update contract_result cr
                set transaction_index = ei.evm_index
                from evm_index ei
                where cr.consensus_timestamp = ei.consensus_timestamp
                  and cr.consensus_timestamp between :consensusStart and :lastConsensusEnd
                  and cr.transaction_index is distinct from ei.evm_index
                returning cr.consensus_timestamp
            ),
            updated_contract_log as (
                update contract_log cl
                set transaction_index = ei.evm_index
                from evm_index ei
                where cl.consensus_timestamp = ei.consensus_timestamp
                  and cl.consensus_timestamp between :consensusStart and :lastConsensusEnd
                  and cl.transaction_index is distinct from ei.evm_index
                returning cl.consensus_timestamp
            )
            select
                (select count(*) from updated_contract_result) as updated_results,
                (select count(*) from updated_contract_log) as updated_logs
            """;

    private static final RowMapper<RecordFileSlice> ROW_MAPPER = new DataClassRowMapper<>(RecordFileSlice.class);
    private static final RowMapper<UpdateCounts> UPDATE_COUNTS_ROW_MAPPER =
            new DataClassRowMapper<>(UpdateCounts.class);

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    @Getter(lazy = true)
    private final long hookContractId = EntityId.of(
                    CommonProperties.getInstance().getShard(),
                    CommonProperties.getInstance().getRealm(),
                    RecordItem.HOOK_CONTRACT_NUM)
            .getId();

    private final long batchInterval;
    private final EntityProperties entityProperties;
    private final boolean v2;
    private long initialUpperBound = -1L;

    ContractLogSyntheticFlagBackfillMigration(
            EntityProperties entityProperties,
            Environment environment,
            ImporterProperties importerProperties,
            DBProperties dbProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.entityProperties = entityProperties;
        batchInterval = DurationStyle.SIMPLE
                .parse(
                        migrationProperties
                                .getParams()
                                .getOrDefault(BATCH_INTERVAL_PROPERTIES_KEY, DEFAULT_BATCH_INTERVAL),
                        ChronoUnit.HOURS)
                .toNanos();
        v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Backfill synthetic flag and EVM transaction index for HAPI-origin contract log rows";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return v2 ? MigrationVersion.fromVersion("2.29.0") : MigrationVersion.fromVersion("1.124.0");
    }

    @Override
    protected boolean performSynchronousSteps() {
        final var persistProperties = entityProperties.getPersist();
        if (!persistProperties.isContracts() || !persistProperties.isContractResults()) {
            return false;
        }

        final var maxConsensusEnd = getJdbcOperations().queryForObject(SELECT_MAX_CONSENSUS_END, Long.class);
        if (maxConsensusEnd == null) {
            log.info("No record files to process, skipping migration");
            return false;
        }

        getJdbcOperations().execute(CREATE_PROGRESS_TABLE);

        final var savedProgress = getJdbcOperations().queryForObject(SELECT_PROGRESS_UPPER_BOUND, Long.class);
        initialUpperBound = savedProgress != null ? savedProgress : maxConsensusEnd;
        log.info("Starting synthetic flag and EVM index backfill with initial timestamp: {}", initialUpperBound);
        return true;
    }

    @NonNull
    @Override
    protected Long getInitial() {
        return initialUpperBound;
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long consensusEndTimestamp) {
        final var consensusStartTimestamp = consensusEndTimestamp - batchInterval;
        final var sliceParams = new MapSqlParameterSource()
                .addValue("consensusEndUpperBound", consensusEndTimestamp)
                .addValue("consensusEndLowerBound", consensusStartTimestamp);
        final var slice = queryForObjectOrNull(SELECT_RECORD_FILES_RANGE, sliceParams, ROW_MAPPER);

        if (slice == null || slice.minConsensusTimestamp() == null || slice.maxConsensusTimestamp() == null) {
            log.info(
                    "No more record files remaining to process. Last consensus end timestamp: {}.",
                    consensusEndTimestamp);
            getJdbcOperations().execute(DROP_PROGRESS_TABLE);
            return Optional.empty();
        }

        final var params = new MapSqlParameterSource()
                .addValue("consensusStart", slice.minConsensusTimestamp())
                .addValue("lastConsensusEnd", slice.maxConsensusTimestamp())
                .addValue("transferSignature", TRANSFER_SIGNATURE_HEX)
                .addValue("approveSignature", APPROVE_SIGNATURE_HEX)
                .addValue("approveForAllSignature", APPROVE_FOR_ALL_SIGNATURE_HEX)
                .addValue("hookContractId", getHookContractId());

        final var backfilled = getNamedParameterJdbcOperations().update(BACKFILL_SYNTHETIC_SQL, params);
        var counts = new UpdateCounts(0, 0);
        if (backfilled > 0) {
            counts = getNamedParameterJdbcOperations()
                    .queryForObject(RECOMPUTE_EVM_TRANSACTION_INDEX_SQL, params, UPDATE_COUNTS_ROW_MAPPER);
        }

        if (backfilled > 0 || counts.updatedResults() > 0 || counts.updatedLogs() > 0) {
            log.info(
                    "Backfilled {} contract_log rows and fixed EVM transaction index for {} contract_result and"
                            + " {} contract_log rows in range [{}, {}]",
                    backfilled,
                    counts.updatedResults(),
                    counts.updatedLogs(),
                    slice.minConsensusTimestamp(),
                    slice.maxConsensusTimestamp());
        }

        getNamedParameterJdbcOperations()
                .update(CHECKPOINT_SQL, new MapSqlParameterSource("upperBound", consensusStartTimestamp));
        return Optional.of(consensusStartTimestamp);
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private record RecordFileSlice(Long minConsensusTimestamp, Long maxConsensusTimestamp) {}

    private record UpdateCounts(long updatedResults, long updatedLogs) {}
}
