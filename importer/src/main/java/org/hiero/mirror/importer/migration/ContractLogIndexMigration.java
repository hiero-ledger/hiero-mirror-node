// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

@Named
public class ContractLogIndexMigration extends AsyncJavaMigration<Long> {

    // 30-day intervals of data to take advantage of the monthly partitioning of record_file and contract_log tables.
    static final long INTERVAL = Duration.ofDays(30).toNanos();

    @Getter
    private final TransactionOperations transactionOperations;

    private final JdbcTemplate jdbcTemplate;

    private static final String CREATE_TEMPORARY_RECORD_FILE_TABLE =
            """
                    create table if not exists processed_record_file_temp(
                        consensus_end bigint not null
                    );
            """;

    private static final String SELECT_LAST_CONSENSUS_END_TIMESTAMP =
            """
                    select coalesce(
                       (select consensus_end from processed_record_file_temp limit 1),
                       (select consensus_end from record_file order by consensus_end desc limit 1),
                       0
                    );
            """;

    private static final String UPDATE_TEMPORARY_RECORD_FILE_TABLE =
            """
                    update processed_record_file_temp
                    set consensus_end = :lastProcessedRecordFileEndTimestamp;
            """;

    private static final String DROP_TEMPORARY_RECORD_FILE_TABLE =
            """
                    drop table if exists processed_record_file_temp;
            """;

    private static final String SELECT_RECORD_FILES_MIN_TIMESTAMP =
            """
                    select consensus_start as min_consensus_timestamp
                    from record_file
                    where consensus_end > :consensusEndLowerBound
                            and consensus_end <= :consensusEndUpperBound
                    order by consensus_end limit 1;
            """;

    private static final String SELECT_RECORD_FILES_MAX_TIMESTAMP =
            """
                    select consensus_end as max_consensus_timestamp
                    from record_file
                    where consensus_end > :consensusEndLowerBound
                            and consensus_end <= :consensusEndUpperBound
                    order by consensus_end desc limit 1;
            """;
    private static final String SELECT_CONTRACT_LOG_INDEXES =
            """
                    with rf as (
                        select consensus_start, consensus_end
                        from record_file
                        where consensus_end >= :consensusStart and consensus_end <= :lastConsensusEnd
                        order by consensus_end
                    ), updated_index as (
                        select
                            cl.contract_id,
                            cl.consensus_timestamp,
                            cl.index as old_contract_log_index,
                            (row_number() over (
                             partition by rf.consensus_end
                             order by cl.consensus_timestamp asc, cl.index asc
                        ) - 1) as new_calculated_index
                        from contract_log cl
                        join rf on cl.consensus_timestamp >= rf.consensus_start
                                          and cl.consensus_timestamp <= rf.consensus_end
                        where cl.consensus_timestamp >= :consensusStart and cl.consensus_timestamp <= :lastConsensusEnd
                        order by cl.consensus_timestamp desc, cl.index desc
                    )
                    select *
                    from updated_index
                    where old_contract_log_index <> new_calculated_index;
            """;

    private static final String UPDATE_CONTRACT_LOG_INDEX =
            """
                     update contract_log cl
                     set "index" = :newIndex
                     where cl.consensus_timestamp = :consensusTimestamp
                         and cl.index = :index;
            """;

    @Lazy
    protected ContractLogIndexMigration(
            DBProperties dbProperties,
            ImporterProperties importerProperties,
            @Owner JdbcTemplate jdbcTemplate,
            TransactionOperations transactionOperations) {
        super(
                importerProperties.getMigration(),
                new NamedParameterJdbcTemplate(jdbcTemplate),
                dbProperties.getSchema());
        this.transactionOperations = transactionOperations;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected Long getInitial() {
        log.info("Create table processed_record_file_temp if not exists.");
        jdbcTemplate.execute(CREATE_TEMPORARY_RECORD_FILE_TABLE);

        final var endTimestamp = jdbcTemplate.queryForObject(SELECT_LAST_CONSENSUS_END_TIMESTAMP, Long.class);
        log.info("Starting migration with initial timestamp: {}.", endTimestamp);
        return endTimestamp;
    }

    @Nonnull
    @Override
    protected Optional<Long> migratePartial(Long lastConsensusEnd) {
        // Get record files for an interval of time.
        final var recordFileSlice = getRecordFilesMinAndMaxTimestamp(lastConsensusEnd);
        if (recordFileSlice.isEmpty()) {
            log.info(
                    "No more record files remaining to process. Last consensus end timestamp: {}. Dropping temporary table processed_record_file_temp.",
                    lastConsensusEnd);
            jdbcTemplate.execute(DROP_TEMPORARY_RECORD_FILE_TABLE);
            return Optional.empty();
        }

        // The record file slice contains only one element.
        final var recordFileSliceObject = recordFileSlice.get();
        final long startConsensusTimestamp = recordFileSliceObject.minConsensusTimestamp();
        final long endConsensusTimestamp = recordFileSliceObject.maxConsensusTimestamp();
        final var params = Map.of(
                "lastConsensusEnd", endConsensusTimestamp,
                "consensusStart", startConsensusTimestamp);

        log.info(
                "Recalculating contract log indexes between {} and {} timestamp.",
                startConsensusTimestamp,
                endConsensusTimestamp);
        final var contractLogs = namedParameterJdbcTemplate.queryForList(SELECT_CONTRACT_LOG_INDEXES, params);
        for (var contractLog : contractLogs) {
            final var consensusTimestamp = (long) contractLog.get("consensus_timestamp");
            final var index = (int) contractLog.get("old_contract_log_index");
            final var newIndex = (long) contractLog.get("new_calculated_index");
            namedParameterJdbcTemplate.update(
                    UPDATE_CONTRACT_LOG_INDEX,
                    Map.of(
                            "consensusTimestamp", consensusTimestamp,
                            "index", index,
                            "newIndex", newIndex));
        }
        namedParameterJdbcTemplate.update(
                UPDATE_TEMPORARY_RECORD_FILE_TABLE,
                Map.of("lastProcessedRecordFileEndTimestamp", endConsensusTimestamp));

        final var nextConsensusEnd = lastConsensusEnd - INTERVAL;
        return Optional.of(nextConsensusEnd);
    }

    @Override
    public String getDescription() {
        return "Recalculate contract log indexes on block level.";
    }

    @VisibleForTesting
    Optional<RecordFileSlice> getRecordFilesMinAndMaxTimestamp(final Long lastConsensusEnd) {
        var params = new MapSqlParameterSource()
                .addValue("consensusEndUpperBound", lastConsensusEnd)
                .addValue("consensusEndLowerBound", lastConsensusEnd - INTERVAL);
        var minTimestamp = queryForObjectOrNull(SELECT_RECORD_FILES_MIN_TIMESTAMP, params, Long.class);
        var maxTimestamp = queryForObjectOrNull(SELECT_RECORD_FILES_MAX_TIMESTAMP, params, Long.class);

        return minTimestamp == null && maxTimestamp == null
                ? Optional.empty()
                : Optional.of(new RecordFileSlice(minTimestamp, maxTimestamp));
    }

    record RecordFileSlice(long minConsensusTimestamp, long maxConsensusTimestamp) {}
}
