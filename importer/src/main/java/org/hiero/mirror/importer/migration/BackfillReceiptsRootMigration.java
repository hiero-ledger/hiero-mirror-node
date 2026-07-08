// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.ToLongFunction;
import org.apache.commons.lang3.ArrayUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.receipt.ReceiptAssembler;
import org.hiero.mirror.importer.parser.record.receipt.ReceiptRootCalculator;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Backfills record_file.receipts_root for every block ingested before the value was computed on ingestion.
 * Blocks still missing the value are processed newest first in batches: each batch reconstructs the blocks' receipts
 * from the persisted contract results and logs and computes the Ethereum receipts-trie root, matching ingestion
 * exactly (blocks with no EVM activity yield 32 zero bytes).
 */
@Named
public class BackfillReceiptsRootMigration extends AsyncJavaMigration<Long> {

    static final int BATCH_SIZE = 100;

    private static final String SELECT_BLOCKS = """
            select consensus_start, consensus_end from record_file
            where consensus_end < :consensusEnd and receipts_root is null
            order by consensus_end desc limit :limit
            """;

    private static final String SELECT_CONTRACT_RESULTS = """
            select bloom, consensus_timestamp, gas_used, transaction_index, transaction_result
            from contract_result
            where consensus_timestamp between :consensusStart and :consensusEnd
            """;

    private static final String SELECT_CONTRACT_LOGS = """
            select consensus_timestamp, contract_id, data, index, topic0, topic1, topic2, topic3, transaction_index
            from contract_log
            where consensus_timestamp between :consensusStart and :consensusEnd
            """;

    private static final String SELECT_ETHEREUM_TRANSACTION_TYPES = """
            select consensus_timestamp, type from ethereum_transaction
            where consensus_timestamp between :consensusStart and :consensusEnd and type is not null
            """;

    private static final String SELECT_TRANSACTION_INDEXES = """
            select consensus_timestamp, index from transaction
            where consensus_timestamp in (:timestamps) and index is not null
            """;

    private static final String UPDATE_RECEIPTS_ROOT =
            "update record_file set receipts_root = :receiptsRoot where consensus_end = :consensusEnd";

    private final ObjectProvider<EntityRepository> entityRepositoryProvider;
    private final ObjectProvider<TransactionOperations> transactionOperationsProvider;
    private final ReceiptAssembler receiptAssembler;
    private final ReceiptRootCalculator receiptRootCalculator;
    private final boolean v2;

    public BackfillReceiptsRootMigration(
            DBProperties dbProperties,
            Environment environment,
            ImporterProperties importerProperties,
            ObjectProvider<JdbcOperations> jdbcOperationsProvider,
            ObjectProvider<EntityRepository> entityRepositoryProvider,
            ObjectProvider<TransactionOperations> transactionOperationsProvider,
            ReceiptAssembler receiptAssembler,
            ReceiptRootCalculator receiptRootCalculator) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.entityRepositoryProvider = entityRepositoryProvider;
        this.transactionOperationsProvider = transactionOperationsProvider;
        this.receiptAssembler = receiptAssembler;
        this.receiptRootCalculator = receiptRootCalculator;
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
                Map.of("consensusEnd", lastConsensusEnd, "limit", BATCH_SIZE),
                (rs, rowNum) -> new BlockRange(rs.getLong("consensus_start"), rs.getLong("consensus_end")));
        if (blocks.isEmpty()) {
            return Optional.empty();
        }

        var rangeParams = Map.<String, Object>of(
                "consensusStart", blocks.getLast().consensusStart(),
                "consensusEnd", blocks.getFirst().consensusEnd());

        var logsMissingIndex = new ArrayList<ContractLog>();
        var contractResults = jdbcOperations.query(SELECT_CONTRACT_RESULTS, rangeParams, this::mapContractResult);
        var contractLogs = jdbcOperations.query(
                SELECT_CONTRACT_LOGS, rangeParams, (rs, rowNum) -> mapContractLog(rs, logsMissingIndex));
        resolveMissingTransactionIndexes(contractResults, logsMissingIndex);

        var transactionTypes = new HashMap<Long, Integer>();
        jdbcOperations.query(SELECT_ETHEREUM_TRANSACTION_TYPES, rangeParams, rs -> {
            transactionTypes.put(rs.getLong("consensus_timestamp"), rs.getInt("type"));
        });

        var blockRanges = new TreeMap<Long, Long>();
        blocks.forEach(block -> blockRanges.put(block.consensusStart(), block.consensusEnd()));
        var resultsByBlock = groupByBlock(contractResults, blockRanges, ContractResult::getConsensusTimestamp);
        var logsByBlock = groupByBlock(contractLogs, blockRanges, ContractLog::getConsensusTimestamp);
        var evmAddresses = resolveEvmAddresses(contractLogs);

        var updates = new MapSqlParameterSource[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.get(i);
            var receipts = receiptAssembler.assemble(
                    resultsByBlock.getOrDefault(block.consensusStart(), List.of()),
                    logsByBlock.getOrDefault(block.consensusStart(), List.of()),
                    transactionTypes,
                    evmAddresses);
            updates[i] = new MapSqlParameterSource()
                    .addValue("receiptsRoot", receiptRootCalculator.calculate(receipts))
                    .addValue("consensusEnd", block.consensusEnd());
        }
        jdbcOperations.batchUpdate(UPDATE_RECEIPTS_ROOT, updates);

        return Optional.of(blocks.getLast().consensusEnd());
    }

    /**
     * Groups items into their enclosing blocks by consensus timestamp, mirroring how {@code ReceiptsRootListener}
     * partitions a multi-file parser batch.
     */
    private static <T> Map<Long, List<T>> groupByBlock(
            Collection<T> items, NavigableMap<Long, Long> consensusEndByStart, ToLongFunction<T> timestampExtractor) {
        var grouped = new HashMap<Long, List<T>>();
        for (var item : items) {
            var timestamp = timestampExtractor.applyAsLong(item);
            var block = consensusEndByStart.floorEntry(timestamp);
            if (block != null && timestamp <= block.getValue()) {
                grouped.computeIfAbsent(block.getKey(), k -> new ArrayList<>()).add(item);
            }
        }

        return grouped;
    }

    private ContractResult mapContractResult(ResultSet rs, int rowNum) throws SQLException {
        var contractResult = new ContractResult();
        contractResult.setBloom(rs.getBytes("bloom"));
        contractResult.setConsensusTimestamp(rs.getLong("consensus_timestamp"));
        contractResult.setGasUsed(rs.getObject("gas_used", Long.class));
        contractResult.setTransactionIndex(rs.getObject("transaction_index", Integer.class));
        contractResult.setTransactionResult(rs.getObject("transaction_result", Integer.class));
        return contractResult;
    }

    private ContractLog mapContractLog(ResultSet rs, List<ContractLog> missingIndex) throws SQLException {
        var contractLog = new ContractLog();
        contractLog.setConsensusTimestamp(rs.getLong("consensus_timestamp"));
        contractLog.setContractId(EntityId.of(rs.getLong("contract_id")));
        contractLog.setData(rs.getBytes("data"));
        contractLog.setIndex(rs.getInt("index"));
        contractLog.setTopic0(rs.getBytes("topic0"));
        contractLog.setTopic1(rs.getBytes("topic1"));
        contractLog.setTopic2(rs.getBytes("topic2"));
        contractLog.setTopic3(rs.getBytes("topic3"));

        var transactionIndex = rs.getObject("transaction_index", Integer.class);
        contractLog.setTransactionIndex(transactionIndex != null ? transactionIndex : 0);
        if (transactionIndex == null) {
            missingIndex.add(contractLog);
        }

        return contractLog;
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

    private Map<Long, byte[]> resolveEvmAddresses(Collection<ContractLog> contractLogs) {
        var ids = new LinkedHashSet<Long>();
        for (var contractLog : contractLogs) {
            if (!EntityId.isEmpty(contractLog.getContractId())) {
                ids.add(contractLog.getContractId().getId());
            }
        }

        if (ids.isEmpty()) {
            return Map.of();
        }

        var addresses = new HashMap<Long, byte[]>(ids.size());
        for (var mapping : entityRepositoryProvider.getObject().findEvmAddressesByIds(ids)) {
            if (!ArrayUtils.isEmpty(mapping.getEvmAddress())) {
                addresses.put(mapping.getId(), mapping.getEvmAddress());
            }
        }

        return addresses;
    }

    private record BlockRange(long consensusStart, long consensusEnd) {}
}
