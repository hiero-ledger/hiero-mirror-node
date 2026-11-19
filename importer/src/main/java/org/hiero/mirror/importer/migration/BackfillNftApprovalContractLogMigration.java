// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.hiero.mirror.common.util.DomainUtils.trim;

import jakarta.inject.Named;
import java.math.BigDecimal;
import java.sql.Types;
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
import org.apache.commons.lang3.ArrayUtils;
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
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.support.DataAccessUtils;
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

    private static final byte[] APPROVAL = Bytes.fromHexString(
                    "8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925")
            .toArray();

    private static final byte[] APPROVAL_FOR_ALL = Bytes.fromHexString(
                    "17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31")
            .toArray();

    private static final RowMapper<Approval> APPROVAL_ROW_MAPPER = new DataClassRowMapper<>(Approval.class);

    private static final String APPROVALS_TABLE = "nft_approval_snapshot_temp";

    private static final RowMapper<Block> BLOCK_ROW_MAPPER = new DataClassRowMapper<>(Block.class);

    private static final RowMapper<ExistingLog> EXISTING_LOG_ROW_MAPPER = new DataClassRowMapper<>(ExistingLog.class);

    private static final RowMapper<IndexedTransaction> INDEXED_TRANSACTION_ROW_MAPPER =
            new DataClassRowMapper<>(IndexedTransaction.class);

    private static final String INSERT_LOG = """
            insert into nft_approval_logs_temp (
              bloom, consensus_timestamp, contract_id, data, index, payer_account_id, restored_position,
              root_contract_id, synthetic, topic0, topic1, topic2, topic3, transaction_hash, transaction_index
            ) values (
              '\\x00', :timestamp, :token, '\\x00', 2147483647, :payer, :position,
              :token, true, :topic0, :topic1, :topic2, :topic3, :hash, :transactionIndex
            )
            """;

    private static final String LOG_COLUMNS = """
            bloom, consensus_timestamp, contract_id, data, index, payer_account_id, root_contract_id,
            synthetic, topic0, topic1, topic2, topic3, transaction_hash, transaction_index
            """;

    private static final String PROGRESS_TABLE = "nft_approval_contract_log_progress_temp";

    // Existing logs keep their block index order, which can differ from timestamp order: the effects of a contract
    // call share its timestamp yet can be logged after other transactions. A recovered approval takes the position
    // found among the existing logs of its own transaction, see position(). When there is none, it is placed right
    // before the first log with a later timestamp, which was always created after the approval was parsed. The
    // original allowance-list order is not retained in NFT history, so recovered approvals sharing a position are
    // ordered by token and serial.
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
                else coalesce(l.restored_position, coalesce((
                  select min(e.index) from nft_approval_logs_temp e
                  where e.index <> 2147483647 and e.consensus_timestamp > l.consensus_timestamp
                ), 2147483647) - 0.5)
                end as position
              from nft_approval_logs_temp l
            ) logs
            """;

    private static final String SELECT_APPROVAL_TIMESTAMPS = """
            select distinct consensus_timestamp from nft_approval_snapshot_temp
            where consensus_timestamp between :start and :end
            """;

    // Materialize the transaction subset before joining the local snapshot in v2. Serial approvals are sparse, so
    // look the transactions up by the snapshot timestamps instead of scanning every transaction in the range.
    // NFT history retains the effective, deduplicated serial approvals even when transaction bytes were not saved.
    // The logs of a scheduled approval triggered by a contract call belong to that call, at its timestamp and
    // transaction index, so such an approval is skipped like any other transaction of contract origin.
    private static final String SELECT_APPROVALS = """
            with approvals as materialized (
              select consensus_timestamp, payer_account_id, transaction_hash
              from transaction
              where consensus_timestamp between :start and :end
                and consensus_timestamp = any(:timestamps::bigint[])
                and type = :type and result = :success
                and (coalesce(nonce, 0) = 0 or scheduled)
                and consensus_timestamp not in (
                  select consensus_timestamp from contract_result
                  where consensus_timestamp between :start and :end
                    and consensus_timestamp = any(:timestamps::bigint[])
                    and (gas_used is not null or child)
                )
                and (parent_consensus_timestamp is null or parent_consensus_timestamp not in (
                  select consensus_timestamp from contract_result
                  where consensus_timestamp between :start and :end and gas_used is not null
                ))
            )
            select r.*, a.payer_account_id, a.transaction_hash
            from nft_approval_snapshot_temp r join approvals a using (consensus_timestamp)
            where r.consensus_timestamp between :start and :end
            order by consensus_timestamp, token_id, serial_number
            """;

    private static final String SELECT_BLOCK = """
            select consensus_start, consensus_end, hapi_version_major, hapi_version_minor, hapi_version_patch
            from record_file
            where consensus_end >= ?
            order by consensus_end
            limit 1
            """;

    private static final String SELECT_LAST_APPROVAL = """
            select max(consensus_timestamp) from nft_approval_snapshot_temp where consensus_timestamp <= ?
            """;

    private static final String SELECT_LOGS = """
            select consensus_timestamp, contract_id, index, topic0, topic1, topic2, topic3
            from contract_log where consensus_timestamp between :start and :end
              and topic0 in (:approval, :approvalForAll)
            """;

    private static final String SELECT_PREVIOUS_BLOCK = """
            select max(consensus_end) from record_file where consensus_end < ?
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

    // Snapshot only the serial approvals restore() can recover: those with an ApprovalForAll log of the same token in
    // their transaction, and delegated ones, which produce no log at all since HAPI 0.77.0. Nearly every other block
    // holding a serial approval needs no fix, so this avoids visiting each of them. restore() checks both conditions
    // again. contract_log is colocated with nft and nft_history by token in v2, so the lookup runs on the shards.
    private static final String SNAPSHOT_APPROVALS = """
            create table if not exists nft_approval_snapshot_temp as
            select account_id, delegating_spender, spender, token_id, serial_number,
              lower(timestamp_range) as consensus_timestamp
            from nft n
            where spender is not null and account_id is not null and lower(timestamp_range) <= :upperBound
              and (delegating_spender is not null or exists (
                select 1 from contract_log l
                where l.contract_id = n.token_id and l.consensus_timestamp = lower(n.timestamp_range)
                  and l.topic0 = :approvalForAll
              ))
            union
            select account_id, delegating_spender, spender, token_id, serial_number,
              lower(timestamp_range) as consensus_timestamp
            from nft_history h
            where spender is not null and account_id is not null and lower(timestamp_range) <= :upperBound
              and (delegating_spender is not null or exists (
                select 1 from contract_log l
                where l.contract_id = h.token_id and l.consensus_timestamp = lower(h.timestamp_range)
                  and l.topic0 = :approvalForAll
              ))
            """;

    private final EntityProperties entityProperties;
    private long initialUpperBound;

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
        final var params =
                new MapSqlParameterSource("upperBound", initialUpperBound).addValue("approvalForAll", APPROVAL_FOR_ALL);
        getNamedParameterJdbcOperations().update(SNAPSHOT_APPROVALS, params);
        getJdbcOperations()
                .execute("create index if not exists nft_approval_snapshot_temp_timestamp on " + APPROVALS_TABLE
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
        // Serial approvals are sparse, so jump to the block of the latest remaining one instead of walking every block
        final var jdbcOperations = getJdbcOperations();
        final var timestamp = jdbcOperations.queryForObject(SELECT_LAST_APPROVAL, Long.class, upperBound);
        final var next = timestamp == null ? null : restoreBlockOf(timestamp);
        if (next == null) {
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

        return true;
    }

    private static byte[] address(final long id, final Map<Long, byte[]> addresses) {
        return addresses.getOrDefault(id, longZeroAddress(id));
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

    // An existing log holds either address format, depending on how EVM address lookup was configured back then
    private static boolean isAddress(final byte @Nullable [] topic, final long id, final Map<Long, byte[]> addresses) {
        if (topic == null) {
            return false;
        }

        final var address = trim(topic);
        return Arrays.equals(address, longZeroAddress(id)) || Arrays.equals(address, addresses.get(id));
    }

    private static byte[] longZeroAddress(final long id) {
        return trim(DomainUtils.toEvmAddress(EntityId.of(id)));
    }

    /**
     * Finds where the importer would have logged a serial approval among the existing logs of its transaction. The
     * importer first walks the NFT allowances, logging the ApprovalForAll of an allowance right before its serial
     * approvals, and only then logs the fungible token approvals.
     *
     * @return the fractional position between two existing block log indexes, or null when the transaction has no log
     */
    private static @Nullable BigDecimal position(
            final Approval approval, final List<ExistingLog> existing, final Map<Long, byte[]> addresses) {
        int allowance = -1;
        int first = Integer.MAX_VALUE;
        int lastNft = -1;
        int token = -1;
        for (final var log : existing) {
            if (log.consensusTimestamp() != approval.consensusTimestamp()) {
                continue;
            }

            first = Math.min(first, log.index());
            if (log.isFungibleApproval()) {
                continue;
            }

            lastNft = Math.max(lastNft, log.index());
            if (!log.isApprovalForAll(approval)) {
                continue;
            }

            token = Math.max(token, log.index());
            if (log.hasOwnerAndSpender(approval, addresses)) {
                allowance = Math.max(allowance, log.index());
            }
        }

        // Right after the ApprovalForAll of its own allowance, or else the last ApprovalForAll of the token
        final int anchor = allowance >= 0 ? allowance : token;
        if (anchor >= 0) {
            return BigDecimal.valueOf(anchor + 0.25);
        }

        // A delegated allowance without an ApprovalForAll log: after the other NFT logs, before the fungible ones
        if (lastNft >= 0) {
            return BigDecimal.valueOf(lastNft + 0.5);
        }

        return first != Integer.MAX_VALUE ? BigDecimal.valueOf(first - 0.5) : null;
    }

    private static MapSqlParameterSource range(final long start, final long end) {
        return new MapSqlParameterSource("start", start).addValue("end", end);
    }

    private void restore(final Block block) {
        final var params = range(block.consensusStart(), block.consensusEnd())
                .addValue("approval", APPROVAL)
                .addValue("approvalForAll", APPROVAL_FOR_ALL)
                .addValue("success", SUCCESS.getNumber())
                .addValue("type", TransactionType.CRYPTOAPPROVEALLOWANCE.getProtoId());
        final var jdbc = getNamedParameterJdbcOperations();
        final var timestamps = jdbc.queryForList(SELECT_APPROVAL_TIMESTAMPS, params, Long.class);
        params.addValue("timestamps", timestamps.toArray(Long[]::new));
        final var candidates = jdbc.query(SELECT_APPROVALS, params, APPROVAL_ROW_MAPPER);
        if (candidates.isEmpty()) {
            return;
        }

        final var existing = jdbc.query(SELECT_LOGS, params, EXISTING_LOG_ROW_MAPPER);
        // Limit the backfill to the serial approvals the importer skipped because approvedForAll was set. Such an
        // allowance produced an ApprovalForAll log for the token, which also proves synthetic logs were persisted for
        // the transaction. The only exception is a delegated allowance with approvedForAll=false since HAPI 0.77.0:
        // it produces no log at all, so a delegated serial approval in that range is restored without one.
        final boolean hapi077OrLater = block.isHapi077OrLater();
        final var missing = candidates.stream()
                .filter(a -> (hapi077OrLater && a.delegatingSpender() != null)
                        || existing.stream().anyMatch(log -> log.isApprovalForAll(a)))
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
                // Insert before the next persisted transaction, or after the last one. Counting preceding rows
                // loses index gaps, e.g. when ingestion began partway through a block.
                newIndexes.computeIfAbsent(
                        timestamp,
                        t -> timestampByIndex.entrySet().stream()
                                .filter(entry -> entry.getValue() > t)
                                .mapToInt(Map.Entry::getKey)
                                .findFirst()
                                .orElse(timestampByIndex.isEmpty() ? 0 : timestampByIndex.lastKey() + 1));
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
        jdbcOperations.execute("""
                create temp table nft_approval_logs_temp (like contract_log, restored_position numeric) on commit drop
                """);
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
                        .addValue("position", position(a, existing, addresses), Types.NUMERIC)
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

    // Returns the upper bound of the next iteration, or null when no earlier block is left
    private @Nullable Long restoreBlockOf(final long timestamp) {
        final var jdbcOperations = getJdbcOperations();
        final var block = DataAccessUtils.singleResult(jdbcOperations.query(SELECT_BLOCK, BLOCK_ROW_MAPPER, timestamp));
        if (block == null || block.consensusStart() > timestamp) {
            // An approval can outlive its record_file row, e.g. with retention. Do not assign it to the next block
            // across the gap. Continue from the previous block, which skips every other approval in the same gap.
            return jdbcOperations.queryForObject(SELECT_PREVIOUS_BLOCK, Long.class, timestamp);
        }

        restore(block);
        return block.consensusStart() - 1;
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        return new TransactionTemplate(
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource())));
    }

    private record Approval(
            long consensusTimestamp,
            long accountId,
            @Nullable Long delegatingSpender,
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
            long consensusTimestamp,
            long contractId,
            int index,
            byte @Nullable [] topic0,
            byte @Nullable [] topic1,
            byte @Nullable [] topic2,
            byte @Nullable [] topic3) {

        boolean hasOwnerAndSpender(final Approval approval, final Map<Long, byte[]> addresses) {
            return isAddress(topic1, approval.accountId(), addresses)
                    && isAddress(topic2, approval.spender(), addresses);
        }

        boolean isApprovalForAll(final Approval approval) {
            return consensusTimestamp == approval.consensusTimestamp()
                    && contractId == approval.tokenId()
                    && Arrays.equals(topic0, APPROVAL_FOR_ALL);
        }

        boolean isFungibleApproval() {
            return Arrays.equals(topic0, APPROVAL) && ArrayUtils.isEmpty(topic3);
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
