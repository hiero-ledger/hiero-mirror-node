// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.aggregator.LogsBloomAggregator;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

@Named
public class BackfillBlockMigration extends AsyncJavaMigration<Long> {

    private static final String SELECT_CONTRACT_RESULT = "select bloom, gas_used " + "from contract_result cr "
            + "join transaction t on t.consensus_timestamp = cr.consensus_timestamp "
            + "where cr.consensus_timestamp >= :consensusStart "
            + "  and cr.consensus_timestamp <= :consensusEnd "
            + "  and t.nonce = 0";

    private static final String SET_TRANSACTION_INDEX = "with indexed as ( "
            + "  select consensus_timestamp, row_number() over (order by consensus_timestamp) - 1 as index "
            + "  from transaction "
            + "  where consensus_timestamp >= :consensusStart"
            + "    and consensus_timestamp <= :consensusEnd "
            + "  order by consensus_timestamp) "
            + "update transaction t "
            + "set index = indexed.index "
            + "from indexed "
            + "where t.consensus_timestamp = indexed.consensus_timestamp";

    private final ObjectProvider<RecordFileRepository> recordFileRepositoryProvider;

    private final ObjectProvider<TransactionOperations> transactionOperationsProvider;

    public BackfillBlockMigration(
            DBProperties dbProperties,
            ImporterProperties importerProperties,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<RecordFileRepository> recordFileRepositoryProvider,
            ObjectProvider<TransactionOperations> transactionOperationsProvider) {
        super(importerProperties.getMigration(), jdbcTemplateProvider, dbProperties.getSchema());
        this.recordFileRepositoryProvider = recordFileRepositoryProvider;
        this.transactionOperationsProvider = transactionOperationsProvider;
    }

    public TransactionOperations getTransactionOperations() {
        return transactionOperationsProvider.getObject();
    }

    @Override
    public String getDescription() {
        return "Backfill block gasUsed, logsBloom, and transaction index";
    }

    @Override
    protected Long getInitial() {
        return Long.MAX_VALUE;
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.61.1");
    }

    /**
     * Backfills information for the record file immediately before the consensus end timestamp of the last record file.
     *
     * @param lastConsensusEnd The consensus end timestamp of the last record file
     * @return The consensus end of the processed record file or null if no record file is processed
     */
    @Nonnull
    @Override
    protected Optional<Long> migratePartial(Long lastConsensusEnd) {
        return recordFileRepositoryProvider.getObject()
                .findLatestMissingGasUsedBefore(lastConsensusEnd)
                .map(recordFile -> {
                    var queryParams = Map.of(
                            "consensusStart",
                            recordFile.getConsensusStart(),
                            "consensusEnd",
                            recordFile.getConsensusEnd());

                    var bloomAggregator = new LogsBloomAggregator();
                    var gasUsedTotal = new AtomicLong(0);
                    getNamedParameterJdbcTemplate().query(SELECT_CONTRACT_RESULT, queryParams, rs -> {
                        bloomAggregator.aggregate(rs.getBytes("bloom"));
                        gasUsedTotal.addAndGet(rs.getLong("gas_used"));
                    });

                    recordFile.setGasUsed(gasUsedTotal.get());
                    recordFile.setLogsBloom(bloomAggregator.getBloom());
                    recordFileRepositoryProvider.getObject().save(recordFile);

                    // set transaction index for the transactions in the record file
                    getNamedParameterJdbcTemplate().update(SET_TRANSACTION_INDEX, queryParams);

                    return recordFile.getConsensusEnd();
                });
    }
}
