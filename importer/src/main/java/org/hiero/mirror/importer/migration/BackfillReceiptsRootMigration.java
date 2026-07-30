// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToLongFunction;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.receipt.ReceiptRoot;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.support.DefaultConversionService;
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
              and cr.transaction_result <> 312
            """;

    private static final String SELECT_CONTRACT_LOGS = """
            with child as materialized (
              select distinct consensus_timestamp from contract_result
              where consensus_timestamp between :consensusStart and :consensusEnd and transaction_nonce <> 0
            )
            select cl.consensus_timestamp, cl.contract_id, cl.data, cl.index, cl.topic0, cl.topic1, cl.topic2,
              cl.topic3, cl.transaction_index, e.evm_address
            from contract_log cl
            left join entity e on e.id = cl.contract_id
            where cl.consensus_timestamp between :consensusStart and :consensusEnd
              and cl.consensus_timestamp not in (select consensus_timestamp from child)
            """;

    private static final String UPDATE_RECEIPTS_ROOT =
            "update record_file set receipts_root = :receiptsRoot where consensus_end = :consensusEnd";

    private static final String CREATE_PROGRESS_TABLE = """
            create table if not exists backfill_receipts_root_progress_temp(
                upper_bound bigint not null
            );
            """;

    private static final String DROP_PROGRESS_TABLE = """
            drop table if exists backfill_receipts_root_progress_temp;
            """;

    private static final String SELECT_PROGRESS_UPPER_BOUND =
            "select (select upper_bound from backfill_receipts_root_progress_temp limit 1)";

    private static final String CHECKPOINT_SQL = """
            with clear_table as (delete from backfill_receipts_root_progress_temp)
            insert into backfill_receipts_root_progress_temp(upper_bound)
            values (:upperBound)
            """;

    private static final String SET_CITUS_LIMIT = "set citus.max_intermediate_result_size = -1";

    private final int batchSize;
    private final EntityProperties entityProperties;
    private final boolean v2;
    private long initialUpperBound = Long.MAX_VALUE;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    public BackfillReceiptsRootMigration(
            DBProperties dbProperties,
            Environment environment,
            EntityProperties entityProperties,
            ImporterProperties importerProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.batchSize = Integer.parseInt(
                migrationProperties.getParams().getOrDefault(BATCH_SIZE_KEY, String.valueOf(DEFAULT_BATCH_SIZE)));
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Invalid non-positive %s %d".formatted(BATCH_SIZE_KEY, batchSize));
        }
        this.entityProperties = entityProperties;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Backfill receipts_root for historical blocks";
    }

    @Override
    protected int getOrder() {
        return 2;
    }

    private TransactionOperations transactionOperations() {
        var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    @Override
    protected Long getInitial() {
        return initialUpperBound;
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The receipts_root column is added by V1.125.0 (v1) and V2.30.0 (v2).
        return v2 ? MigrationVersion.fromVersion("2.30.0") : MigrationVersion.fromVersion("1.125.0");
    }

    @Override
    protected boolean performSynchronousSteps() {
        if (!entityProperties.getPersist().isContractResults()) {
            log.info("Skipping receipts_root backfill since contract result persistence is disabled");
            return false;
        }

        getJdbcOperations().execute(CREATE_PROGRESS_TABLE);
        var savedProgress = getJdbcOperations().queryForObject(SELECT_PROGRESS_UPPER_BOUND, Long.class);
        initialUpperBound = savedProgress != null ? savedProgress : Long.MAX_VALUE;
        log.info("Starting receipts_root backfill with initial upper bound: {}", initialUpperBound);
        return true;
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long lastConsensusEnd) {
        var jdbcOperations = getNamedParameterJdbcOperations();
        if (v2) {
            getJdbcOperations().execute(SET_CITUS_LIMIT);
        }

        var blocks = jdbcOperations.query(
                SELECT_BLOCKS,
                Map.of("consensusEnd", lastConsensusEnd, "limit", batchSize),
                (rs, rowNum) -> new BlockRange(rs.getLong("consensus_start"), rs.getLong("consensus_end")));
        if (blocks.isEmpty()) {
            jdbcOperations.getJdbcOperations().execute(DROP_PROGRESS_TABLE);
            return Optional.empty();
        }

        var rangeParams = Map.<String, Object>of(
                "consensusStart", blocks.getLast().consensusStart(),
                "consensusEnd", blocks.getFirst().consensusEnd());

        var rows = jdbcOperations.query(SELECT_CONTRACT_RESULTS, rangeParams, CONTRACT_RESULT_ROW_MAPPER);
        var contractResults = new ArrayList<ContractResult>(rows);

        var logRows = jdbcOperations.query(SELECT_CONTRACT_LOGS, rangeParams, CONTRACT_LOG_ROW_MAPPER);
        var contractLogs = new ArrayList<ContractLog>(logRows.size());
        var evmAddresses = new HashMap<Long, byte[]>();
        for (var row : logRows) {
            contractLogs.add(row);
            if (!ArrayUtils.isEmpty(row.getEvmAddress()) && !EntityId.isEmpty(row.getContractId())) {
                evmAddresses.put(row.getContractId().getId(), row.getEvmAddress());
            }
        }

        var blockRanges = new TreeMap<Long, Long>();
        blocks.forEach(block -> blockRanges.put(block.consensusStart(), block.consensusEnd()));
        var resultsByBlock = groupByBlock(contractResults, blockRanges, ContractResult::getConsensusTimestamp);
        var logsByBlock = groupByBlock(contractLogs, blockRanges, ContractLog::getConsensusTimestamp);

        // Persisted logs already carry resolved topics/addresses, so the receipts root is computed directly from the
        // evm addresses joined into the log query; no further resolution is needed during the backfill.
        var updates = new MapSqlParameterSource[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.get(i);
            var receiptRoot = new ReceiptRoot(evmAddresses);
            putReceipts(
                    receiptRoot,
                    resultsByBlock.getOrDefault(block.consensusStart(), List.of()),
                    logsByBlock.getOrDefault(block.consensusStart(), List.of()));
            updates[i] = new MapSqlParameterSource()
                    .addValue("receiptsRoot", receiptRoot.getRootHash())
                    .addValue("consensusEnd", block.consensusEnd());
        }
        jdbcOperations.batchUpdate(UPDATE_RECEIPTS_ROOT, updates);

        var nextUpperBound = blocks.getLast().consensusEnd();
        jdbcOperations.update(CHECKPOINT_SQL, new MapSqlParameterSource("upperBound", nextUpperBound));
        return Optional.of(nextUpperBound);
    }

    // Groups items into their enclosing blocks by consensus timestamp. An item belongs to the block whose consensus
    // range [start, end] contains its timestamp; items falling outside every block are dropped.
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

    // Groups the block's persisted results and logs into per-transaction receipts and adds them to the trie in
    // ascending transaction-index order, matching how ingestion streams each top-level transaction.
    private static void putReceipts(
            ReceiptRoot receiptRoot, List<ContractResult> contractResults, List<ContractLog> contractLogs) {
        var resultsByIndex = new TreeMap<Integer, ContractResult>();
        for (var contractResult : contractResults) {
            if (contractResult.getTransactionIndex() != null) {
                resultsByIndex.put(contractResult.getTransactionIndex(), contractResult);
            }
        }

        var logsByIndex = new HashMap<Integer, List<ContractLog>>();
        for (var contractLog : contractLogs) {
            if (contractLog.getTransactionIndex() != null) {
                logsByIndex
                        .computeIfAbsent(contractLog.getTransactionIndex(), k -> new ArrayList<>())
                        .add(contractLog);
            }
        }

        var indexes = new TreeSet<Integer>();
        indexes.addAll(resultsByIndex.keySet());
        indexes.addAll(logsByIndex.keySet());

        for (var index : indexes) {
            var contractResult = resultsByIndex.get(index);
            var type = contractResult instanceof ContractResultAndType typed ? typed.getType() : null;
            var ethereumTransaction =
                    type != null ? EthereumTransaction.builder().type(type).build() : null;
            receiptRoot.put(contractResult, logsByIndex.getOrDefault(index, List.of()), ethereumTransaction);
        }
    }

    private static <T> RowMapper<T> rowMapper(Class<T> type) {
        var conversionService = new DefaultConversionService();
        conversionService.addConverter(
                Long.class, EntityId.class, EntityIdConverter.INSTANCE::convertToEntityAttribute);
        var mapper = new DataClassRowMapper<>(type);
        mapper.setConversionService(conversionService);
        return mapper;
    }

    private record BlockRange(long consensusStart, long consensusEnd) {}

    @Data
    static class ContractResultAndType extends ContractResult {
        private Integer type;
    }

    @Data
    static class ContractLogAndEvmAddress extends ContractLog {
        private byte[] evmAddress;
    }
}
