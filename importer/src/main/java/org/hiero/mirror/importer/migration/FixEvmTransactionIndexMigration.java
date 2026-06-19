// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.transaction.RecordItem;
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

    // 38-bit entity num mask, see EntityId.NUM_BITS. RecordItem.HOOK_CONTRACT_NUM is compared against this masked
    // value (i.e. against ContractID.getContractNum(), ignoring shard/realm), so the same masking is used here.
    private static final long ENTITY_NUM_MASK = (1L << 38) - 1;

    // A descendant (at any depth) inherits its nearest preceding root's index. Since a root always increments the
    // running count and a descendant never does, "count of roots seen so far, minus 1" gives every row -- root or
    // descendant -- the correct index in a single pass, mirroring how RecordFileParser.setEvmTransactionIndex()
    // only increments its counter for items whose RecordItem.getContractRelatedParent() is null. is_root mirrors
    // that same null check, including the RecordItem.hookParent backward-scan exception for hook descendants
    // parented back to the original trigger rather than to the hook itself.
    private static final String EVM_INDEX_CTE = """
            with parent_timestamps_with_contract_result as (
                select consensus_timestamp
                from contract_result
                where consensus_timestamp >= :consensusStart
                  and consensus_timestamp <= :lastConsensusEnd
            ),
            hook_dispatch_timestamps as (
                select consensus_timestamp
                from contract_result
                where consensus_timestamp >= :consensusStart
                  and consensus_timestamp <= :lastConsensusEnd
                  and (contract_id & %1$d) = %2$d
            ),
            evm_candidates as (
                select
                    t.consensus_timestamp,
                    t.parent_consensus_timestamp,
                    t.consensus_timestamp in (select consensus_timestamp from hook_dispatch_timestamps)
                        as is_hook_dispatch,
                    (
                        t.parent_consensus_timestamp is null
                        or t.parent_consensus_timestamp not in (
                            select consensus_timestamp from parent_timestamps_with_contract_result
                        )
                    ) as is_orphaned_parent
                from transaction t
                where t.consensus_timestamp >= :consensusStart
                  and t.consensus_timestamp <= :lastConsensusEnd
                  and t.type in (7, 8, 50)
            ),
            hook_attribution as (
                select
                    consensus_timestamp,
                    max(case when is_hook_dispatch then consensus_timestamp end) over (
                        partition by parent_consensus_timestamp
                        order by consensus_timestamp
                    ) as nearest_hook_timestamp
                from evm_candidates
                where is_orphaned_parent
                  and parent_consensus_timestamp is not null
            ),
            evm_roots as (
                select
                    ec.consensus_timestamp,
                    case
                        when not ec.is_orphaned_parent then false
                        when ec.parent_consensus_timestamp is null then true
                        when ec.is_hook_dispatch then true
                        when ha.nearest_hook_timestamp is null then true
                        else false
                    end as is_root
                from evm_candidates ec
                left join hook_attribution ha on ha.consensus_timestamp = ec.consensus_timestamp
            ),
            evm_index as (
                select
                    er.consensus_timestamp,
                    sum(case when er.is_root then 1 else 0 end) over (
                        partition by rf.consensus_end
                        order by er.consensus_timestamp
                    ) - 1 as evm_index
                from evm_roots er
                join record_file rf
                    on er.consensus_timestamp between rf.consensus_start and rf.consensus_end
            )
            """.formatted(ENTITY_NUM_MASK, RecordItem.HOOK_CONTRACT_NUM);

    private static final String UPDATE_CONTRACT_RESULT_SQL = EVM_INDEX_CTE + """
            update contract_result cr
            set transaction_index = ei.evm_index
            from evm_index ei
            where cr.consensus_timestamp = ei.consensus_timestamp
              and cr.consensus_timestamp >= :consensusStart
              and cr.consensus_timestamp <= :lastConsensusEnd
            """;

    private static final String UPDATE_CONTRACT_LOG_SQL = EVM_INDEX_CTE + """
            update contract_log cl
            set transaction_index = ei.evm_index
            from evm_index ei
            where cl.consensus_timestamp = ei.consensus_timestamp
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
