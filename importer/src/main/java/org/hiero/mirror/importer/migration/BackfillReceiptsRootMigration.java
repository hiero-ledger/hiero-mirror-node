// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.receipt.ReceiptBlockUtils;
import org.hiero.mirror.importer.parser.record.receipt.ReceiptRoot;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Backfills record_file.receipts_root for every block ingested before the value was computed on ingestion.
 * Blocks still missing the value are processed newest first in batches: each batch reconstructs the blocks' receipts
 * from the persisted contract results and logs and computes the Ethereum receipts-trie root, matching ingestion
 * exactly (blocks with no EVM activity yield 32 zero bytes).
 */
@Named
final class BackfillReceiptsRootMigration extends AsyncJavaMigration<Long> {

    static final int DEFAULT_BATCH_SIZE = 100;
    private static final String BATCH_SIZE_KEY = "batchSize";
    private static final RowMapper<ContractLogAndEvmAddress> CONTRACT_LOG_ROW_MAPPER =
            rowMapper(ContractLogAndEvmAddress.class);
    private static final RowMapper<ContractResultAndType> CONTRACT_RESULT_ROW_MAPPER =
            rowMapper(ContractResultAndType.class);

    private static final String SELECT_BLOCKS = """
            select consensus_start, consensus_end from record_file
            where consensus_end < :consensusEnd and receipts_root is null
            order by consensus_end desc limit :limit
            """;

    // transaction_nonce = 0 excludes child contract results, matching the relay's behaviour.
    // The materialized CTE keeps the join valid on citus, which rejects direct joins
    // between tables distributed on different columns.
    private static final String SELECT_CONTRACT_RESULTS = """
            with et as materialized (
              select consensus_timestamp, type from ethereum_transaction
              where consensus_timestamp between :consensusStart and :consensusEnd and type is not null
            )
            select cr.bloom, cr.consensus_timestamp, cr.gas_used, cr.transaction_index, cr.transaction_result, et.type
            from contract_result cr
            left join et on et.consensus_timestamp = cr.consensus_timestamp
            where cr.consensus_timestamp between :consensusStart and :consensusEnd and cr.transaction_nonce = 0
            """;

    private static final String SELECT_CONTRACT_LOGS = """
            select cl.consensus_timestamp, cl.contract_id, cl.data, cl.index, cl.topic0, cl.topic1, cl.topic2,
              cl.topic3, cl.transaction_index, e.evm_address
            from contract_log cl
            left join entity e on e.id = cl.contract_id
            where cl.consensus_timestamp between :consensusStart and :consensusEnd
            """;

    private static final String SELECT_TRANSACTION_INDEXES = """
            select consensus_timestamp, index from transaction
            where consensus_timestamp in (:timestamps) and index is not null
            """;

    private static final String UPDATE_RECEIPTS_ROOT =
            "update record_file set receipts_root = :receiptsRoot where consensus_end = :consensusEnd";

    private final int batchSize;
    private final ObjectProvider<TransactionOperations> transactionOperationsProvider;
    private final boolean v2;

    public BackfillReceiptsRootMigration(
            DBProperties dbProperties,
            Environment environment,
            ImporterProperties importerProperties,
            ObjectProvider<JdbcOperations> jdbcOperationsProvider,
            ObjectProvider<TransactionOperations> transactionOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.batchSize = Integer.parseInt(
                migrationProperties.getParams().getOrDefault(BATCH_SIZE_KEY, String.valueOf(DEFAULT_BATCH_SIZE)));
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Invalid non-positive %s %d".formatted(BATCH_SIZE_KEY, batchSize));
        }
        this.transactionOperationsProvider = transactionOperationsProvider;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Backfill receipts_root for historical blocks";
    }

    @Override
    protected TransactionOperations getTransactionOperations() {
        return transactionOperationsProvider.getObject();
    }

    @Override
    protected Long getInitial() {
        return Long.MAX_VALUE;
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The receipts_root column is added by V1.125.0 (v1) and V2.30.0 (v2).
        return v2 ? MigrationVersion.fromVersion("2.30.0") : MigrationVersion.fromVersion("1.125.0");
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long lastConsensusEnd) {
        var jdbcOperations = getNamedParameterJdbcOperations();
        var blocks = jdbcOperations.query(
                SELECT_BLOCKS,
                Map.of("consensusEnd", lastConsensusEnd, "limit", batchSize),
                (rs, rowNum) -> new BlockRange(rs.getLong("consensus_start"), rs.getLong("consensus_end")));
        if (blocks.isEmpty()) {
            return Optional.empty();
        }

        var rangeParams = Map.<String, Object>of(
                "consensusStart", blocks.getLast().consensusStart(),
                "consensusEnd", blocks.getFirst().consensusEnd());

        var rows = jdbcOperations.query(SELECT_CONTRACT_RESULTS, rangeParams, CONTRACT_RESULT_ROW_MAPPER);
        var contractResults = new ArrayList<ContractResult>(rows.size());
        var transactionTypes = new HashMap<Long, Integer>();
        for (var row : rows) {
            contractResults.add(row);
            if (row.getType() != null) {
                transactionTypes.put(row.getConsensusTimestamp(), row.getType());
            }
        }

        var logRows = jdbcOperations.query(SELECT_CONTRACT_LOGS, rangeParams, CONTRACT_LOG_ROW_MAPPER);
        var contractLogs = new ArrayList<ContractLog>(logRows.size());
        var evmAddresses = new HashMap<Long, byte[]>();
        var logsMissingIndex = new ArrayList<ContractLog>();
        for (var row : logRows) {
            contractLogs.add(row);
            if (row.getTransactionIndex() == null) {
                logsMissingIndex.add(row);
            }
            if (!ArrayUtils.isEmpty(row.getEvmAddress()) && !EntityId.isEmpty(row.getContractId())) {
                evmAddresses.put(row.getContractId().getId(), row.getEvmAddress());
            }
        }
        resolveMissingTransactionIndexes(contractResults, logsMissingIndex);

        var blockRanges = new TreeMap<Long, Long>();
        blocks.forEach(block -> blockRanges.put(block.consensusStart(), block.consensusEnd()));
        var resultsByBlock =
                ReceiptBlockUtils.groupByBlock(contractResults, blockRanges, ContractResult::getConsensusTimestamp);
        var logsByBlock = ReceiptBlockUtils.groupByBlock(contractLogs, blockRanges, ContractLog::getConsensusTimestamp);

        var updates = new MapSqlParameterSource[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.get(i);
            var receiptsRoot = ReceiptRoot.of(
                            resultsByBlock.getOrDefault(block.consensusStart(), List.of()),
                            logsByBlock.getOrDefault(block.consensusStart(), List.of()),
                            transactionTypes,
                            evmAddresses)
                    .getRootHash();
            updates[i] = new MapSqlParameterSource()
                    .addValue("receiptsRoot", receiptsRoot)
                    .addValue("consensusEnd", block.consensusEnd());
        }
        jdbcOperations.batchUpdate(UPDATE_RECEIPTS_ROOT, updates);

        return Optional.of(blocks.getLast().consensusEnd());
    }

    private static <T> RowMapper<T> rowMapper(Class<T> type) {
        var conversionService = new DefaultConversionService();
        conversionService.addConverter(
                Long.class, EntityId.class, EntityIdConverter.INSTANCE::convertToEntityAttribute);
        var mapper = new DataClassRowMapper<>(type);
        mapper.setConversionService(conversionService);
        return mapper;
    }

    private void resolveMissingTransactionIndexes(
            Collection<ContractResult> contractResults, Collection<ContractLog> logsMissingIndex) {
        var resultsMissingIndex = new ArrayList<ContractResult>();
        var timestamps = new HashSet<Long>();
        for (var contractResult : contractResults) {
            if (contractResult.getTransactionIndex() == null) {
                resultsMissingIndex.add(contractResult);
                timestamps.add(contractResult.getConsensusTimestamp());
            }
        }
        for (var contractLog : logsMissingIndex) {
            timestamps.add(contractLog.getConsensusTimestamp());
        }

        if (timestamps.isEmpty()) {
            return;
        }

        var indexes = new HashMap<Long, Integer>();
        getNamedParameterJdbcOperations().query(SELECT_TRANSACTION_INDEXES, Map.of("timestamps", timestamps), rs -> {
            indexes.put(rs.getLong("consensus_timestamp"), rs.getInt("index"));
        });

        for (var contractResult : resultsMissingIndex) {
            contractResult.setTransactionIndex(indexes.getOrDefault(contractResult.getConsensusTimestamp(), 0));
        }
        for (var contractLog : logsMissingIndex) {
            contractLog.setTransactionIndex(indexes.getOrDefault(contractLog.getConsensusTimestamp(), 0));
        }
    }

    private record BlockRange(long consensusStart, long consensusEnd) {}

    @Getter
    @Setter
    static class ContractResultAndType extends ContractResult {
        private Integer type;
    }

    @Getter
    @Setter
    static class ContractLogAndEvmAddress extends ContractLog {
        private byte[] evmAddress;
    }
}
