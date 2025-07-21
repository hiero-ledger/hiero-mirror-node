// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

@Named
public class ContractLogIndexMigration extends AsyncJavaMigration<Long> {

    private final RecordFileRepository recordFileRepository;

    @Getter
    private final TransactionOperations transactionOperations;

    private static final RowMapper<RecordFile> RECORD_FILE_ROW_MAPPER = new DataClassRowMapper<>(RecordFile.class);

    // 30-day intervals of data to take advantage of the monthly partitioning of record_file and contract_log tables.
    static final long INTERVAL = Duration.ofDays(30).toNanos();

    private static final String SELECT_RECORD_FILES =
            """
                    select *
                    from record_file
                    where consensus_end > :consensusEndLowerBound and consensus_end <= :consensusEndUpperBound
            """;
    private static final String SELECT_CONTRACT_LOG_INDEXES =
            """
                    select
                        cl.consensus_timestamp,
                        cl.index as old_contract_log_index,
                        (row_number() over (
                         partition by rf.consensus_end
                         order by cl.consensus_timestamp asc, cl.index asc
                    ) - 1) as new_calculated_index
                    from contract_log cl
                    join record_file rf on cl.consensus_timestamp >= rf.consensus_start
                                      and cl.consensus_timestamp <= rf.consensus_end
                    where cl.consensus_timestamp >= :consensusStart and cl.consensus_timestamp <= :lastConsensusEnd
                    order by cl.consensus_timestamp desc, cl.index desc
            """;

    private static final String UPDATE_CONTRACT_LOG_INDEX =
            """
                     update contract_log cl
                     set "index" = :newIndex
                     where cl.consensus_timestamp = :consensusTimestamp
                         and cl.index = :index
            """;

    @Lazy
    protected ContractLogIndexMigration(
            DBProperties dbProperties,
            ImporterProperties importerProperties,
            @Owner JdbcTemplate jdbcTemplate,
            RecordFileRepository recordFileRepository,
            TransactionOperations transactionOperations) {
        super(
                importerProperties.getMigration(),
                new NamedParameterJdbcTemplate(jdbcTemplate),
                dbProperties.getSchema());
        this.recordFileRepository = recordFileRepository;
        this.transactionOperations = transactionOperations;
    }

    @Override
    protected Long getInitial() {
        return recordFileRepository
                .findLatest()
                .map(RecordFile::getConsensusEnd)
                .orElse(0L);
    }

    @Nonnull
    @Override
    protected Optional<Long> migratePartial(Long lastConsensusEnd) {
        // Get record files for an interval of time.
        final var recordFiles = getRecordFiles(lastConsensusEnd);
        if (recordFiles.isEmpty()) {
            return Optional.empty();
        }

        // The record files are sorted, so we can determine the min and max timestamp only from the first and the last
        // record file to avoid a sort in the query.
        final long endConsensusTimestamp = getMaxEndRecordFileTimestamp(recordFiles);
        final long startConsensusTimestamp = getMinStartRecordFileTimestamp(recordFiles);
        final var params = Map.of(
                "lastConsensusEnd", endConsensusTimestamp,
                "consensusStart", startConsensusTimestamp);
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

        final var nextConsensusEnd = lastConsensusEnd - INTERVAL;
        if (nextConsensusEnd < 0) {
            return Optional.of(0L);
        }
        return Optional.of(nextConsensusEnd);
    }

    @Override
    public String getDescription() {
        return "Recalculate contract log indexes on block level.";
    }

    @VisibleForTesting
    List<RecordFile> getRecordFiles(final Long lastConsensusEnd) {
        var params = new MapSqlParameterSource()
                .addValue("consensusEndUpperBound", lastConsensusEnd)
                .addValue("consensusEndLowerBound", lastConsensusEnd - INTERVAL);
        return namedParameterJdbcTemplate.query(SELECT_RECORD_FILES, params, RECORD_FILE_ROW_MAPPER);
    }

    private long getMaxEndRecordFileTimestamp(final List<RecordFile> recordFiles) {
        return Math.max(
                recordFiles.getFirst().getConsensusEnd(), recordFiles.getLast().getConsensusEnd());
    }

    private long getMinStartRecordFileTimestamp(final List<RecordFile> recordFiles) {
        return Math.min(
                recordFiles.getFirst().getConsensusStart(),
                recordFiles.getLast().getConsensusStart());
    }
}
