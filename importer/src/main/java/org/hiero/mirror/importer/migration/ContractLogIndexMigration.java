// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Named
final class ContractLogIndexMigration extends AsyncJavaMigration<Long> {

    static final long INTERVAL = Duration.ofDays(7).toNanos();

    private static final String CREATE_TEMPORARY_PROCESSED_RECORD_FILE_TABLE =
            """
                    create table if not exists processed_record_file_temp(
                        consensus_end bigint not null
                    );
            """;

    private static final String SELECT_LAST_PROCESSED_TIMESTAMP =
            """
                    select coalesce(
                       (select consensus_end from processed_record_file_temp order by consensus_end asc limit 1),
                       (select consensus_end from record_file order by consensus_end desc limit 1),
                       0
                    );
            """;

    private static final String DROP_TEMPORARY_RECORD_FILE_TABLE =
            """
                    drop table if exists processed_record_file_temp;
            """;

    private static final String SELECT_RECORD_FILES_MIN_AND_MAX_TIMESTAMP =
            """
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
                    limit 1;
            """;

    private static final String UPDATE_CONTRACT_LOG_INDEXES =
            """
                    begin;

                    -- set v2 specific property conditionally on the environment
                    %s
                    set local temp_buffers = '64MB';
                    create temp table if not exists contract_log_migration(like contract_log) on commit drop;

                    with rf as (
                      select consensus_start, consensus_end
                      from record_file
                      where consensus_end >= :consensusStart and consensus_end <= :lastConsensusEnd
                      order by consensus_end
                    ), updated_index as (
                      select
                          cl.contract_id,
                          cl.consensus_timestamp,
                          cl.index as old_index,
                          (row_number() over (
                           partition by rf.consensus_end
                           order by cl.consensus_timestamp, cl.index
                      ) - 1) as new_index
                      from contract_log cl
                      join rf on cl.consensus_timestamp >= rf.consensus_start
                                        and cl.consensus_timestamp <= rf.consensus_end
                      where cl.consensus_timestamp >= :consensusStart and cl.consensus_timestamp <= :lastConsensusEnd
                      order by cl.consensus_timestamp, cl.index
                    ), changed as (
                        select *
                        from updated_index
                        where old_index <> new_index
                    ), changed_stat as (
                        select consensus_timestamp, max(old_index) as max_old, min(new_index) as min_new
                        from changed
                        group by consensus_timestamp
                    ), non_conflict as (
                        select *
                        from changed
                        where consensus_timestamp in (select consensus_timestamp from changed_stat where max_old < min_new)
                    ), fix_non_conflict as (
                        update contract_log as cl
                        set index = new_index
                        from non_conflict as n
                        where cl.consensus_timestamp = n.consensus_timestamp and cl.index = n.old_index
                          and cl.consensus_timestamp >= :consensusStart and cl.consensus_timestamp <= :lastConsensusEnd
                    ), conflict as (
                        select *
                        from changed
                        where consensus_timestamp in (select consensus_timestamp from changed_stat where max_old >= min_new)
                    ), deleted as (
                        delete from contract_log as cl
                        using conflict as c
                        where cl.consensus_timestamp = c.consensus_timestamp and cl.index = c.old_index
                          and cl.consensus_timestamp >= :consensusStart and cl.consensus_timestamp <= :lastConsensusEnd
                        returning cl.*, c.new_index
                    )
                    insert into contract_log_migration (
                        bloom,
                        consensus_timestamp,
                        contract_id,
                        data,
                        index,
                        payer_account_id,
                        root_contract_id,
                        topic0,
                        topic1,
                        topic2,
                        topic3,
                        transaction_hash,
                        transaction_index
                    )
                    select
                        bloom,
                        consensus_timestamp,
                        contract_id,
                        data,
                        new_index,
                        payer_account_id,
                        root_contract_id,
                        topic0,
                        topic1,
                        topic2,
                        topic3,
                        transaction_hash,
                        transaction_index
                    from deleted;

                    insert into contract_log
                    select * from contract_log_migration;

                    -- Save the timestamp from where to resume the migration if necessary.
                    insert into processed_record_file_temp(consensus_end)
                    values(:consensusStart);

                    commit;
            """;

    private static final String V2_PROPERTY_MAX_INTERMEDIATE_RESULTS = "set citus.max_intermediate_result_size = -1;";

    private static final RowMapper<RecordFileSlice> ROW_MAPPER = new DataClassRowMapper<>(RecordFileSlice.class);

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    private final JdbcTemplate jdbcTemplate;
    private final EntityProperties entityProperties;
    private final boolean v2;

    @Lazy
    protected ContractLogIndexMigration(
            final Environment environment,
            final DBProperties dbProperties,
            final ImporterProperties importerProperties,
            final @Owner JdbcTemplate jdbcTemplate,
            final EntityProperties entityProperties) {
        super(
                importerProperties.getMigration(),
                new NamedParameterJdbcTemplate(jdbcTemplate),
                dbProperties.getSchema());
        this.jdbcTemplate = jdbcTemplate;
        this.entityProperties = entityProperties;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Recalculate contract log indexes on block level.";
    }

    @Override
    protected Long getInitial() {
        log.info("Create table processed_record_file_temp if not exists.");
        jdbcTemplate.execute(CREATE_TEMPORARY_PROCESSED_RECORD_FILE_TABLE);

        final var endTimestamp = jdbcTemplate.queryForObject(SELECT_LAST_PROCESSED_TIMESTAMP, Long.class);
        log.info("Starting migration with initial timestamp: {}.", endTimestamp);
        return endTimestamp;
    }

    @Nonnull
    @Override
    protected Optional<Long> migratePartial(Long consensusEndTimestamp) {
        // Get record files for an interval of time.
        var consensusStartTimestamp = consensusEndTimestamp - INTERVAL;
        var recordFileSliceParams = new MapSqlParameterSource()
                .addValue("consensusEndUpperBound", consensusEndTimestamp)
                .addValue("consensusEndLowerBound", consensusStartTimestamp);
        final var recordFileSlice =
                queryForObjectOrNull(SELECT_RECORD_FILES_MIN_AND_MAX_TIMESTAMP, recordFileSliceParams, ROW_MAPPER);
        if (recordFileSlice == null) {
            log.info(
                    "No more record files remaining to process. Last consensus end timestamp: {}."
                            + "Dropping temporary table processed_record_file_temp.",
                    consensusEndTimestamp);
            jdbcTemplate.execute(DROP_TEMPORARY_RECORD_FILE_TABLE);
            return Optional.empty();
        }

        // The record file slice contains only one element.
        final long sliceStartTimestamp = recordFileSlice.minConsensusTimestamp();
        final long sliceEndTimestamp = recordFileSlice.maxConsensusTimestamp();
        log.info(
                "Recalculating contract log indexes between {} and {} timestamp.",
                sliceStartTimestamp,
                sliceEndTimestamp);

        // Update the contract log entries for the given timestamp range.
        final var params = Map.of(
                "lastConsensusEnd", sliceEndTimestamp,
                "consensusStart", sliceStartTimestamp);
        namedParameterJdbcTemplate.update(getVersionedContractUpdateQuery(), params);

        return Optional.of(consensusStartTimestamp);
    }

    @Override
    protected boolean performSynchronousSteps() {
        final var persistProperties = entityProperties.getPersist();
        return persistProperties.isContracts() && persistProperties.isContractResults();
    }

    private String getVersionedContractUpdateQuery() {
        return String.format(
                UPDATE_CONTRACT_LOG_INDEXES, v2 ? V2_PROPERTY_MAX_INTERMEDIATE_RESULTS : StringUtils.EMPTY);
    }

    private TransactionOperations transactionOperations() {
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private record RecordFileSlice(long minConsensusTimestamp, long maxConsensusTimestamp) {}
}
