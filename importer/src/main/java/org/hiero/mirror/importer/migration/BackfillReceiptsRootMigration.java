// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.commons.lang3.ArrayUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.receipt.ReceiptAssembler;
import org.hiero.mirror.importer.parser.record.receipt.ReceiptBlockUtils;
import org.hiero.mirror.importer.parser.record.receipt.ReceiptRootCalculator;
import org.hiero.mirror.importer.repository.EntityRepository;
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
    private static final RowMapper<ContractLog> CONTRACT_LOG_ROW_MAPPER = rowMapper(ContractLog.class);
    private static final RowMapper<ContractResult> CONTRACT_RESULT_ROW_MAPPER = rowMapper(ContractResult.class);

    private static final String SELECT_BLOCKS = """
            select consensus_start, consensus_end from record_file
            where consensus_end < :consensusEnd and receipts_root is null
            order by consensus_end desc limit :limit
            """;

    // transaction_nonce = 0 excludes child (internal) contract results, matching the relay's behaviour
    private static final String SELECT_CONTRACT_RESULTS = """
            select bloom, consensus_timestamp, gas_used, transaction_index, transaction_result
            from contract_result
            where consensus_timestamp between :consensusStart and :consensusEnd and transaction_nonce = 0
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

    private final int batchSize;
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
        this.batchSize = Integer.parseInt(
                migrationProperties.getParams().getOrDefault(BATCH_SIZE_KEY, String.valueOf(DEFAULT_BATCH_SIZE)));
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Invalid non-positive %s %d".formatted(BATCH_SIZE_KEY, batchSize));
        }
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
                Map.of("consensusEnd", lastConsensusEnd, "limit", batchSize),
                (rs, rowNum) -> new BlockRange(rs.getLong("consensus_start"), rs.getLong("consensus_end")));
        if (blocks.isEmpty()) {
            return Optional.empty();
        }

        var rangeParams = Map.<String, Object>of(
                "consensusStart", blocks.getLast().consensusStart(),
                "consensusEnd", blocks.getFirst().consensusEnd());

        var contractResults = jdbcOperations.query(SELECT_CONTRACT_RESULTS, rangeParams, CONTRACT_RESULT_ROW_MAPPER);
        var contractLogs = jdbcOperations.query(SELECT_CONTRACT_LOGS, rangeParams, CONTRACT_LOG_ROW_MAPPER);
        var logsMissingIndex = new ArrayList<ContractLog>();
        for (var contractLog : contractLogs) {
            if (contractLog.getTransactionIndex() == null) {
                logsMissingIndex.add(contractLog);
            }
        }
        resolveMissingTransactionIndexes(contractResults, logsMissingIndex);

        var transactionTypes = new HashMap<Long, Integer>();
        jdbcOperations.query(SELECT_ETHEREUM_TRANSACTION_TYPES, rangeParams, rs -> {
            transactionTypes.put(rs.getLong("consensus_timestamp"), rs.getInt("type"));
        });

        var blockRanges = new TreeMap<Long, Long>();
        blocks.forEach(block -> blockRanges.put(block.consensusStart(), block.consensusEnd()));
        var resultsByBlock =
                ReceiptBlockUtils.groupByBlock(contractResults, blockRanges, ContractResult::getConsensusTimestamp);
        var logsByBlock = ReceiptBlockUtils.groupByBlock(contractLogs, blockRanges, ContractLog::getConsensusTimestamp);
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
