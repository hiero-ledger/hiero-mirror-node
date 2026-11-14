// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.hiero.mirror.common.util.DomainUtils.trim;

import jakarta.inject.Named;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.util.Version;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/** Backfills serial approvals independently of the collection-wide allowance flag. */
@Named
@NullMarked
final class BackfillNftApprovalContractLogMigration extends AsyncJavaMigration<Long> {

    static final Duration DEFAULT_BATCH_INTERVAL = Duration.ofHours(1);

    private static final byte[] APPROVAL = Bytes.fromHexString(
                    "8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925")
            .toArray();

    private static final byte[] APPROVAL_FOR_ALL = Bytes.fromHexString(
                    "17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31")
            .toArray();

    private static final RowMapper<Approval> APPROVAL_ROW_MAPPER = new DataClassRowMapper<>(Approval.class);

    private static final String APPROVALS_TABLE = "nft_approval_snapshot_temp";

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";

    private static final RowMapper<Block> BLOCK_ROW_MAPPER = new DataClassRowMapper<>(Block.class);

    private static final RowMapper<ExistingLog> EXISTING_LOG_ROW_MAPPER = new DataClassRowMapper<>(ExistingLog.class);

    private static final RowMapper<IndexedTransaction> INDEXED_TRANSACTION_ROW_MAPPER =
            new DataClassRowMapper<>(IndexedTransaction.class);

    private static final String INSERT_LOG = """
            insert into nft_approval_logs_temp (
              bloom, consensus_timestamp, contract_id, data, index, payer_account_id, root_contract_id,
              synthetic, topic0, topic1, topic2, topic3, transaction_hash, transaction_index
            ) values (
              '\\x00', :timestamp, :token, '\\x00', 2147483647, :payer, :token,
              true, :topic0, :topic1, :topic2, :topic3, :hash, :transactionIndex
            )
            """;

    private static final String LOG_COLUMNS = """
            bloom, consensus_timestamp, contract_id, data, index, payer_account_id, root_contract_id,
            synthetic, topic0, topic1, topic2, topic3, transaction_hash, transaction_index
            """;

    private static final String PROGRESS_TABLE = "nft_approval_contract_log_progress_temp";

    // Existing logs keep their block index order, which can differ from timestamp order: the effects of a contract
    // call share its timestamp yet can be logged after other transactions. A log with a later timestamp was always
    // created after the approval was parsed, so the recovered approvals are placed right before the first such log,
    // after every existing log of the approval itself. The original allowance-list order is not retained in NFT
    // history, so recovered approvals of the same transaction are ordered by token and serial.
    private static final String RESTORE_LOGS = """
            insert into contract_log (
              bloom, consensus_timestamp, contract_id, data, index, payer_account_id, root_contract_id,
              synthetic, topic0, topic1, topic2, topic3, transaction_hash, transaction_index
            )
            select bloom, consensus_timestamp, contract_id, data,
              row_number() over (
                order by position, consensus_timestamp, contract_id, length(topic3), topic3
              ) - 1,
              payer_account_id, root_contract_id, synthetic, topic0, topic1, topic2, topic3,
              transaction_hash, transaction_index
            from (
              select l.*,
                case when l.index <> 2147483647 then l.index::numeric
                else coalesce((
                  select min(e.index) from nft_approval_logs_temp e
                  where e.index <> 2147483647 and e.consensus_timestamp > l.consensus_timestamp
                ), 2147483647) - 0.5
                end as position
              from nft_approval_logs_temp l
            ) logs
            """;

    // Materialize the transaction subset before joining the local snapshot in v2.
    // NFT history retains the effective, deduplicated serial approvals even when transaction bytes were not saved.
    private static final String SELECT_APPROVALS = """
            with approvals as materialized (
              select consensus_timestamp, payer_account_id, transaction_hash
              from transaction
              where consensus_timestamp between :start and :end
                and type = :type and result = :success
                and (coalesce(nonce, 0) = 0 or scheduled)
                and consensus_timestamp not in (
                  select consensus_timestamp from contract_result
                  where consensus_timestamp between :start and :end
                    and (gas_used is not null or child)
                )
            )
            select r.*, a.payer_account_id, a.transaction_hash
            from nft_approval_snapshot_temp r join approvals a using (consensus_timestamp)
            where r.consensus_timestamp between :start and :end
            order by consensus_timestamp, token_id, serial_number
            """;

    private static final String SELECT_BLOCKS = """
            select consensus_start, consensus_end, hapi_version_major, hapi_version_minor, hapi_version_patch
            from record_file
            where consensus_end > ? and consensus_end <= ?
            order by consensus_end
            """;

    private static final String SELECT_LOGS = """
            select consensus_timestamp, contract_id, topic0, topic3
            from contract_log where consensus_timestamp between :start and :end
              and topic0 in (:approval, :approvalForAll)
            """;

    private static final String SELECT_TRANSACTION_INDEXES = """
            select consensus_timestamp, transaction_index from contract_result
            where consensus_timestamp between :start and :end and transaction_index is not null
            union
            select consensus_timestamp, transaction_index from contract_log
            where consensus_timestamp between :start and :end and transaction_index is not null
            """;

    private static final String SHIFT_TRANSACTION_INDEXES = """
            update %s set transaction_index = transaction_index + (
              select count(*) from unnest(:positions::int[]) p where p <= transaction_index
            ) where consensus_timestamp between :start and :end and transaction_index is not null
            """;

    private static final String SNAPSHOT_APPROVALS = """
            create table if not exists nft_approval_snapshot_temp as
            select account_id, spender, token_id, serial_number, lower(timestamp_range) as consensus_timestamp
            from nft
            where spender is not null and account_id is not null and lower(timestamp_range) <= ?
            union
            select account_id, spender, token_id, serial_number, lower(timestamp_range) as consensus_timestamp
            from nft_history
            where spender is not null and account_id is not null and lower(timestamp_range) <= ?
            """;

    private final long batchInterval;
    private final EntityProperties entityProperties;
    private long initialUpperBound;
    private long lowerBound;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    private final boolean v2;

    BackfillNftApprovalContractLogMigration(
            final DBProperties dbProperties,
            final EntityProperties entityProperties,
            final Environment environment,
            final ImporterProperties importerProperties,
            @Owner final ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.batchInterval = DurationStyle.SIMPLE
                .parse(
                        migrationProperties
                                .getParams()
                                .getOrDefault(BATCH_INTERVAL_PROPERTIES_KEY, DEFAULT_BATCH_INTERVAL.toHours() + "h"),
                        ChronoUnit.HOURS)
                .toNanos();
        this.entityProperties = entityProperties;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Restore missing NFT Approval contract logs";
    }

    @Override
    protected Long getInitial() {
        // Snapshot both sources once instead of scanning NFT revisions for every batch. A single statement sees a
        // consistent view even as concurrent updates move revisions into nft_history; UNION deduplicates revisions.
        // Keep this indexed snapshot across restarts, alongside the progress checkpoint.
        final var jdbcOperations = getJdbcOperations();
        jdbcOperations.update(SNAPSHOT_APPROVALS, initialUpperBound, initialUpperBound);
        jdbcOperations.execute("create index if not exists nft_approval_snapshot_temp_timestamp on " + APPROVALS_TABLE
                + " (consensus_timestamp)");
        return initialUpperBound;
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // Requires contract_result.child, and contract_log without its dropped child column for the staging table
        return v2 ? MigrationVersion.fromVersion("2.35.2") : MigrationVersion.fromVersion("1.130.2");
    }

    @Override
    protected Optional<Long> migratePartial(final Long upperBound) {
        final long next = Math.max(0, upperBound - batchInterval);
        final var jdbcOperations = getJdbcOperations();
        final var blocks = jdbcOperations.query(SELECT_BLOCKS, BLOCK_ROW_MAPPER, next, upperBound);

        if (!blocks.isEmpty()) {
            final var params = range(
                            blocks.getFirst().consensusStart(), blocks.getLast().consensusEnd())
                    .addValue("type", TransactionType.CRYPTOAPPROVEALLOWANCE.getProtoId())
                    .addValue("success", SUCCESS.getNumber());
            final var approvals =
                    getNamedParameterJdbcOperations().query(SELECT_APPROVALS, params, APPROVAL_ROW_MAPPER);

            // Both lists are ordered by timestamp, so each block takes the approvals up to its consensus end
            int start = 0;
            for (final var block : blocks) {
                int end = start;
                while (end < approvals.size() && approvals.get(end).consensusTimestamp() <= block.consensusEnd()) {
                    end++;
                }

                if (end > start) {
                    restore(block, approvals.subList(start, end));
                }

                start = end;
            }
        }

        if (next < lowerBound || next == 0) {
            jdbcOperations.execute("drop table " + APPROVALS_TABLE);
            jdbcOperations.execute("drop table " + PROGRESS_TABLE);
            return Optional.empty();
        }

        jdbcOperations.update("delete from " + PROGRESS_TABLE);
        jdbcOperations.update("insert into " + PROGRESS_TABLE + " values (?)", next);
        return Optional.of(next);
    }

    @Override
    protected boolean performSynchronousSteps() {
        final var jdbcOperations = getJdbcOperations();
        if (!entityProperties.getPersist().isSyntheticContractLogs()) {
            // Clean up after an earlier run that was interrupted before synthetic logs were disabled
            jdbcOperations.execute("drop table if exists " + APPROVALS_TABLE);
            jdbcOperations.execute("drop table if exists " + PROGRESS_TABLE);
            return false;
        }

        final var end = jdbcOperations.queryForObject("select max(consensus_end) from record_file", Long.class);
        if (end == null) {
            return false;
        }

        jdbcOperations.execute("create table if not exists " + PROGRESS_TABLE + " (upper_bound bigint not null)");
        final var saved = jdbcOperations.queryForObject(
                "select (select upper_bound from " + PROGRESS_TABLE + " limit 1)", Long.class);
        initialUpperBound = saved == null ? end : saved;
        if (saved == null) {
            // Freeze the cutoff before building the snapshot, including when interrupted before the first batch.
            jdbcOperations.update("insert into " + PROGRESS_TABLE + " values (?)", initialUpperBound);
        }

        lowerBound = Objects.requireNonNull(
                jdbcOperations.queryForObject("select min(consensus_end) from record_file", Long.class));
        return true;
    }

    private static byte[] address(final long id, final Map<Long, byte[]> addresses) {
        return addresses.getOrDefault(id, trim(DomainUtils.toEvmAddress(EntityId.of(id))));
    }

    private Map<Long, byte[]> addresses(final List<Approval> approvals) {
        final var result = new HashMap<Long, byte[]>();
        if (!entityProperties.getPersist().isSyntheticContractLogEvmAddressLookup()) {
            return result;
        }

        final var ids = approvals.stream()
                .flatMap(a -> Stream.of(a.accountId(), a.spender()))
                .collect(Collectors.toSet());
        getNamedParameterJdbcOperations()
                .query(
                        "select id, evm_address from entity where id in (:ids) and length(evm_address) > 0",
                        new MapSqlParameterSource("ids", ids),
                        // A block body selects the RowCallbackHandler overload, an expression body is ambiguous
                        rs -> {
                            result.put(rs.getLong(1), trim(rs.getBytes(2)));
                        });
        return result;
    }

    private static MapSqlParameterSource range(final long start, final long end) {
        return new MapSqlParameterSource("start", start).addValue("end", end);
    }

    private void restore(final Block block, final List<Approval> approvals) {
        // A retained transaction can outlive its record_file row. Do not assign it to the next block across a gap.
        final var candidates = approvals.stream()
                .filter(a -> a.consensusTimestamp() >= block.consensusStart())
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        final var params = range(block.consensusStart(), block.consensusEnd())
                .addValue("approval", APPROVAL)
                .addValue("approvalForAll", APPROVAL_FOR_ALL);
        final var jdbc = getNamedParameterJdbcOperations();
        final var existing = jdbc.query(SELECT_LOGS, params, EXISTING_LOG_ROW_MAPPER);
        // Limit the backfill to the serial approvals the importer skipped because approvedForAll was set. Before HAPI
        // 0.77.0 such an allowance always produced an ApprovalForAll log for the token, which also proves synthetic
        // logs were persisted for the transaction. Since HAPI 0.77.0 a delegated allowance with approvedForAll=false
        // produces no log at all, yet any missing serial Approval in that range can only stem from the same defect.
        final boolean hapi077OrLater = block.isHapi077OrLater();
        final var missing = candidates.stream()
                .filter(a -> hapi077OrLater || existing.stream().anyMatch(log -> log.isApprovalForAll(a)))
                .filter(a -> existing.stream().noneMatch(log -> log.matches(a)))
                .toList();
        if (missing.isEmpty()) {
            return;
        }

        final var addresses = addresses(missing);
        final var indexed = jdbc.query(SELECT_TRANSACTION_INDEXES, params, INDEXED_TRANSACTION_ROW_MAPPER);
        final var indexByTimestamp = new HashMap<Long, Integer>();
        final var timestampByIndex = new TreeMap<Integer, Long>();
        for (final var row : indexed) {
            indexByTimestamp.put(row.consensusTimestamp(), row.transactionIndex());
            timestampByIndex.merge(row.transactionIndex(), row.consensusTimestamp(), Math::min);
        }

        final var newIndexes = new TreeMap<Long, Integer>();
        for (final var approval : missing) {
            final long timestamp = approval.consensusTimestamp();
            if (!indexByTimestamp.containsKey(timestamp)) {
                newIndexes.computeIfAbsent(timestamp, t -> (int) timestampByIndex.values().stream()
                        .filter(previous -> previous < t)
                        .count());
            }
        }

        final var positions = newIndexes.values().toArray(Integer[]::new);
        params.addValue("positions", positions);
        indexByTimestamp.replaceAll((_, index) ->
                index + (int) Arrays.stream(positions).filter(p -> p <= index).count());
        int offset = 0;
        for (final var entry : newIndexes.entrySet()) {
            indexByTimestamp.put(entry.getKey(), entry.getValue() + offset++);
        }

        // Staging avoids primary-key collisions while shifting log indexes. The caller commits the block changes
        // and the migration checkpoint together, so an interrupted migration can safely retry the batch.
        final var jdbcOperations = getJdbcOperations();
        jdbcOperations.execute("create temp table nft_approval_logs_temp (like contract_log) on commit drop");
        jdbc.update(
                "insert into nft_approval_logs_temp (" + LOG_COLUMNS + ") select " + LOG_COLUMNS
                        + " from contract_log where consensus_timestamp between :start and :end",
                params);
        if (positions.length > 0) {
            jdbc.update(SHIFT_TRANSACTION_INDEXES.formatted("contract_result"), params);
            jdbc.update(SHIFT_TRANSACTION_INDEXES.formatted("nft_approval_logs_temp"), params);
            final var updates = newIndexes.keySet().stream()
                    .map(timestamp -> new MapSqlParameterSource("timestamp", timestamp)
                            .addValue("index", indexByTimestamp.get(timestamp)))
                    .toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate(
                    "update contract_result set transaction_index = :index where consensus_timestamp = :timestamp",
                    updates);
            jdbc.batchUpdate(
                    "update nft_approval_logs_temp set transaction_index = :index where consensus_timestamp = :timestamp",
                    updates);
        }

        final var logs = missing.stream()
                .map(a -> new MapSqlParameterSource()
                        .addValue("timestamp", a.consensusTimestamp())
                        .addValue("token", a.tokenId())
                        .addValue("payer", a.payerAccountId())
                        .addValue("topic0", APPROVAL)
                        .addValue("topic1", address(a.accountId(), addresses))
                        .addValue("topic2", address(a.spender(), addresses))
                        .addValue(
                                "topic3",
                                trim(Bytes.ofUnsignedLong(a.serialNumber()).toArray()))
                        .addValue("hash", a.transactionHash() == null ? null : Arrays.copyOf(a.transactionHash(), 32))
                        .addValue("transactionIndex", indexByTimestamp.get(a.consensusTimestamp())))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT_LOG, logs);
        jdbc.update("delete from contract_log where consensus_timestamp between :start and :end", params);
        jdbcOperations.update(RESTORE_LOGS);
        jdbcOperations.execute("drop table nft_approval_logs_temp");
        log.info("Restored {} NFT approval logs in block ending at {}", missing.size(), block.consensusEnd());
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        return new TransactionTemplate(
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource())));
    }

    private record Approval(
            long consensusTimestamp,
            long accountId,
            long spender,
            long tokenId,
            long serialNumber,
            long payerAccountId,
            byte @Nullable [] transactionHash) {}

    private record Block(
            long consensusStart,
            long consensusEnd,
            @Nullable Integer hapiVersionMajor,
            @Nullable Integer hapiVersionMinor,
            @Nullable Integer hapiVersionPatch) {

        boolean isHapi077OrLater() {
            return hapiVersionMajor != null
                    && hapiVersionMinor != null
                    && hapiVersionPatch != null
                    && new Version(hapiVersionMajor, hapiVersionMinor, hapiVersionPatch)
                            .isGreaterThanOrEqualTo(RecordFile.HAPI_VERSION_0_77_0);
        }
    }

    private record ExistingLog(
            long consensusTimestamp, long contractId, byte @Nullable [] topic0, byte @Nullable [] topic3) {

        boolean isApprovalForAll(final Approval approval) {
            return consensusTimestamp == approval.consensusTimestamp()
                    && contractId == approval.tokenId()
                    && Arrays.equals(topic0, APPROVAL_FOR_ALL);
        }

        boolean matches(final Approval approval) {
            // A serial has only one effective approval per transaction. Do not duplicate an existing event merely
            // because EVM address lookup was configured differently when that event was originally imported.
            return consensusTimestamp == approval.consensusTimestamp()
                    && contractId == approval.tokenId()
                    && Arrays.equals(topic0, APPROVAL)
                    && Arrays.equals(
                            trim(topic3),
                            trim(Bytes.ofUnsignedLong(approval.serialNumber()).toArray()));
        }
    }

    private record IndexedTransaction(long consensusTimestamp, int transactionIndex) {}
}
