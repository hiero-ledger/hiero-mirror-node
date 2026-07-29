// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
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

    private final int batchSize;
    private final boolean v2;
    private long initialUpperBound = Long.MAX_VALUE;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    public BackfillReceiptsRootMigration(
            DBProperties dbProperties,
            Environment environment,
            ImporterProperties importerProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.batchSize = Integer.parseInt(
                migrationProperties.getParams().getOrDefault(BATCH_SIZE_KEY, String.valueOf(DEFAULT_BATCH_SIZE)));
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Invalid non-positive %s %d".formatted(BATCH_SIZE_KEY, batchSize));
        }
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
        for (var row : logRows) {
            contractLogs.add(row);
            if (!ArrayUtils.isEmpty(row.getEvmAddress()) && !EntityId.isEmpty(row.getContractId())) {
                evmAddresses.put(row.getContractId().getId(), row.getEvmAddress());
            }
        }

        var blockRanges = new TreeMap<Long, Long>();
        blocks.forEach(block -> blockRanges.put(block.consensusStart(), block.consensusEnd()));
        var resultsByBlock =
                ReceiptBlockUtils.groupByBlock(contractResults, blockRanges, ContractResult::getConsensusTimestamp);
        var logsByBlock = ReceiptBlockUtils.groupByBlock(contractLogs, blockRanges, ContractLog::getConsensusTimestamp);

        var updates = new MapSqlParameterSource[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.get(i);
            var receiptRoot = new ReceiptRoot();
            resultsByBlock.getOrDefault(block.consensusStart(), List.of()).forEach(receiptRoot::add);
            logsByBlock.getOrDefault(block.consensusStart(), List.of()).forEach(receiptRoot::add);
            updates[i] = new MapSqlParameterSource()
                    .addValue("receiptsRoot", receiptRoot.getRootHash(transactionTypes, evmAddresses))
                    .addValue("consensusEnd", block.consensusEnd());
        }
        jdbcOperations.batchUpdate(UPDATE_RECEIPTS_ROOT, updates);

        var nextUpperBound = blocks.getLast().consensusEnd();
        jdbcOperations.update(CHECKPOINT_SQL, new MapSqlParameterSource("upperBound", nextUpperBound));
        return Optional.of(nextUpperBound);
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
