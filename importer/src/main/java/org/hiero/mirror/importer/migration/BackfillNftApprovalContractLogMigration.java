// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.hiero.mirror.common.util.DomainUtils.trim;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tuweni.bytes.Bytes;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.entity.EntityId;
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
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SimplePropertySqlParameterSource;
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

    private static final RowMapper<BlockLog> BLOCK_LOG_ROW_MAPPER = new DataClassRowMapper<>(BlockLog.class);

    private static final RowMapper<Block> BLOCK_ROW_MAPPER = new DataClassRowMapper<>(Block.class);

    private static final String DEFAULT_MAX_BLOCKS = "100";

    // Deletes the logs whose index changes, returning them with their new index to insert them again
    private static final String DELETE_MOVED_LOGS = """
            delete from contract_log l
            using unnest(:timestamps::bigint[], :indexes::int[], :newIndexes::int[])
              as m(consensus_timestamp, index, new_index)
            where l.consensus_timestamp between :start and :end
              and l.consensus_timestamp = m.consensus_timestamp and l.index = m.index
            returning l.bloom, l.consensus_timestamp, l.contract_id, l.data, m.new_index as index, l.payer_account_id,
              l.root_contract_id, l.synthetic, l.topic0, l.topic1, l.topic2, l.topic3, l.transaction_hash,
              l.transaction_index
            """;

    private static final RowMapper<ExistingLog> EXISTING_LOG_ROW_MAPPER = new DataClassRowMapper<>(ExistingLog.class);

    private static final RowMapper<IndexedTransaction> INDEXED_TRANSACTION_ROW_MAPPER =
            new DataClassRowMapper<>(IndexedTransaction.class);

    private static final String INSERT_LOG = """
            insert into contract_log (
              bloom, consensus_timestamp, contract_id, data, index, payer_account_id, root_contract_id,
              synthetic, topic0, topic1, topic2, topic3, transaction_hash, transaction_index
            ) values (
              :bloom, :consensusTimestamp, :contractId, :data, :index, :payerAccountId, :rootContractId,
              :synthetic, :topic0, :topic1, :topic2, :topic3, :transactionHash, :transactionIndex
            )
            """;

    private static final RowMapper<LogRow> LOG_ROW_MAPPER = new DataClassRowMapper<>(LogRow.class);

    private static final String MAX_BLOCKS_KEY = "maxBlocks";

    private static final String PROGRESS_TABLE = "nft_approval_contract_log_progress_temp";

    // The original allowance-list order is not retained in NFT history, so recovered approvals sharing a position are
    // ordered by token and serial
    private static final Comparator<RestoredLog> RESTORED_LOG_ORDER = Comparator.comparingDouble(RestoredLog::position)
            .thenComparingLong(r -> r.approval().consensusTimestamp())
            .thenComparingLong(r -> r.approval().tokenId())
            .thenComparingLong(r -> r.approval().serialNumber());

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
            select r.account_id, r.consensus_timestamp, r.serial_number, r.spender, r.token_id, a.payer_account_id,
              a.transaction_hash
            from nft_approval_snapshot_temp r join approvals a using (consensus_timestamp)
            where r.consensus_timestamp between :start and :end
            order by consensus_timestamp, token_id, serial_number
            """;

    private static final String SELECT_BLOCK = """
            select consensus_start, consensus_end
            from record_file
            where consensus_end >= ?
            order by consensus_end
            limit 1
            """;

    private static final String SELECT_BLOCK_LOGS = """
            select consensus_timestamp, index from contract_log where consensus_timestamp between :start and :end
            order by index
            """;

    private static final String SELECT_LAST_APPROVAL = """
            select max(consensus_timestamp) from nft_approval_snapshot_temp where consensus_timestamp <= ?
            """;

    private static final String SELECT_LOGS = """
            select consensus_timestamp, contract_id, index, topic0, topic1, topic2, topic3
            from contract_log where consensus_timestamp between :start and :end
              and consensus_timestamp = any(:timestamps::bigint[])
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
            order by consensus_timestamp, transaction_index
            """;

    // Snapshot only the serial approvals that can miss their log: those with an ApprovalForAll log of the same token in
    // their transaction, and delegated ones, which produce no log at all since HAPI 0.77.0. Nearly every other block
    // holding a serial approval needs no fix, so this avoids visiting each of them. contract_log is colocated with nft
    // and nft_history by token in v2, so the lookup runs on the shards.
    private static final String SNAPSHOT_APPROVALS = """
            create table if not exists nft_approval_snapshot_temp as
            select account_id, spender, token_id, serial_number, lower(timestamp_range) as consensus_timestamp
            from nft n
            where spender is not null and account_id is not null and lower(timestamp_range) <= :upperBound
              and (delegating_spender is not null or exists (
                select 1 from contract_log l
                where l.contract_id = n.token_id and l.consensus_timestamp = lower(n.timestamp_range)
                  and l.topic0 = :approvalForAll
              ))
            union
            select account_id, spender, token_id, serial_number, lower(timestamp_range) as consensus_timestamp
            from nft_history h
            where spender is not null and account_id is not null and lower(timestamp_range) <= :upperBound
              and (delegating_spender is not null or exists (
                select 1 from contract_log l
                where l.contract_id = h.token_id and l.consensus_timestamp = lower(h.timestamp_range)
                  and l.topic0 = :approvalForAll
              ))
            """;

    // A shifted transaction keeps the rows saved without an index as they are, while an inserted one gets its index on
    // all of its rows, none of which has one
    private static final String UPDATE_TRANSACTION_INDEXES = """
            update %s t set transaction_index = c.transaction_index
            from unnest(:timestamps::bigint[], :indexes::int[], :inserted::boolean[])
              as c(consensus_timestamp, transaction_index, inserted)
            where t.consensus_timestamp between :start and :end and t.consensus_timestamp = c.consensus_timestamp
              and (c.inserted or t.transaction_index is not null)
            """;

    private final EntityProperties entityProperties;
    private long initialUpperBound;
    private int maxBlocks;

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
        return initialUpperBound;
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // Requires contract_result.child, and contract_log without its dropped child column for the moved logs
        return v2 ? MigrationVersion.fromVersion("2.35.2") : MigrationVersion.fromVersion("1.130.2");
    }

    @Override
    protected Optional<Long> migratePartial(final Long upperBound) {
        // Restore up to maxBlocks blocks per call, so the block changes and the checkpoint commit together per batch
        final var jdbcOperations = getJdbcOperations();
        int blocks = 0;
        long lowest = upperBound;
        int restored = 0;
        Long next = upperBound;
        while (next != null && blocks < maxBlocks) {
            // Serial approvals are sparse, so jump to the block of the latest remaining one instead of every block
            final var timestamp = jdbcOperations.queryForObject(SELECT_LAST_APPROVAL, Long.class, next);
            if (timestamp == null) {
                next = null;
                break;
            }

            final var block =
                    DataAccessUtils.singleResult(jdbcOperations.query(SELECT_BLOCK, BLOCK_ROW_MAPPER, timestamp));
            if (block == null || block.consensusStart() > timestamp) {
                // An approval can outlive its record_file row, e.g. with retention. Do not assign it to the next block
                // across the gap. Continue from the previous block, which skips every other approval in the same gap.
                next = jdbcOperations.queryForObject(SELECT_PREVIOUS_BLOCK, Long.class, timestamp);
                continue;
            }

            restored += restore(block);
            blocks++;
            lowest = block.consensusStart();
            next = lowest - 1;
        }

        if (restored > 0) {
            log.info("Restored {} NFT approval logs in {} blocks down to {}", restored, blocks, lowest);
        }

        if (next == null) {
            jdbcOperations.execute("drop table " + APPROVALS_TABLE);
            jdbcOperations.execute("drop table " + PROGRESS_TABLE);
            return Optional.empty();
        }

        jdbcOperations.update(
                "with deleted as (delete from " + PROGRESS_TABLE + ") insert into " + PROGRESS_TABLE + " values (?)",
                next);
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

        maxBlocks = Integer.parseInt(migrationProperties.getParams().getOrDefault(MAX_BLOCKS_KEY, DEFAULT_MAX_BLOCKS));
        if (maxBlocks <= 0) {
            throw new IllegalArgumentException("Invalid non-positive max blocks " + maxBlocks);
        }

        jdbcOperations.execute("create table if not exists " + PROGRESS_TABLE + " (upper_bound bigint not null)");
        final var saved = jdbcOperations.queryForObject(
                "select (select upper_bound from " + PROGRESS_TABLE + " limit 1)", Long.class);
        initialUpperBound = saved == null ? end : saved;
        if (saved == null) {
            // Freeze the cutoff before building the snapshot, including when interrupted before the first batch.
            jdbcOperations.update("insert into " + PROGRESS_TABLE + " values (?)", initialUpperBound);
        }

        // Snapshot both sources once instead of scanning NFT revisions for every batch. A single statement sees a
        // consistent view even as concurrent updates move revisions into nft_history; UNION deduplicates revisions.
        // Keep this indexed snapshot across restarts, alongside the progress checkpoint.
        final var params =
                new MapSqlParameterSource("upperBound", initialUpperBound).addValue("approvalForAll", APPROVAL_FOR_ALL);
        getNamedParameterJdbcOperations().update(SNAPSHOT_APPROVALS, params);
        jdbcOperations.execute("create index if not exists nft_approval_snapshot_temp_timestamp on " + APPROVALS_TABLE
                + " (consensus_timestamp)");
        return true;
    }

    private static byte[] address(final long id, final Map<Long, byte[]> addresses) {
        return addresses.getOrDefault(id, longZeroAddress(id));
    }

    private Map<Long, byte[]> addresses(final Map<Long, List<Approval>> approvals) {
        final var result = new HashMap<Long, byte[]>();
        if (!entityProperties.getPersist().isSyntheticContractLogEvmAddressLookup()) {
            return result;
        }

        final var ids = approvals.values().stream()
                .flatMap(List::stream)
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

    /**
     * Inserts an Approval log for each missing serial approval at its position among the block's logs. Existing logs
     * keep their block index order, which can differ from timestamp order: the effects of a contract call share its
     * timestamp yet can be logged after other transactions. An existing log only moves up by the number of restored logs
     * placed before it, so only the moved logs can collide with another primary key. Those are deleted and inserted
     * again, while the others stay in place.
     *
     * @return the number of restored logs
     */
    private int insertLogs(
            final SortedMap<Long, List<Approval>> missing,
            final List<ExistingLog> existing,
            final Map<Long, Integer> transactionIndexes,
            final MapSqlParameterSource params) {
        final var jdbc = getNamedParameterJdbcOperations();
        final var blockLogs = jdbc.query(SELECT_BLOCK_LOGS, params, BLOCK_LOG_ROW_MAPPER);
        final var addresses = addresses(missing);
        final var restored = new ArrayList<RestoredLog>();
        for (final var approvals : missing.values()) {
            for (final var approval : approvals) {
                restored.add(new RestoredLog(position(approval, existing, blockLogs, addresses), approval));
            }
        }

        restored.sort(RESTORED_LOG_ORDER);
        final var timestamps = new ArrayList<Long>();
        final var indexes = new ArrayList<Integer>();
        final var newIndexes = new ArrayList<Integer>();
        int before = 0;
        for (final var log : blockLogs) {
            while (before < restored.size() && restored.get(before).position() < log.index()) {
                before++;
            }

            if (before == 0) {
                continue;
            }

            timestamps.add(log.consensusTimestamp());
            indexes.add(log.index());
            newIndexes.add(log.index() + before);
        }

        final var inserts = new ArrayList<LogRow>();
        if (!timestamps.isEmpty()) {
            final var moveParams = new MapSqlParameterSource(params.getValues())
                    .addValue("timestamps", timestamps.toArray(Long[]::new))
                    .addValue("indexes", indexes.toArray(Integer[]::new))
                    .addValue("newIndexes", newIndexes.toArray(Integer[]::new));
            inserts.addAll(jdbc.query(DELETE_MOVED_LOGS, moveParams, LOG_ROW_MAPPER));
        }

        // Each restored log lands after the existing logs below its position and the restored logs placed before it
        for (int i = 0; i < restored.size(); i++) {
            final var a = restored.get(i).approval();
            inserts.add(new LogRow(
                    new byte[] {0},
                    a.consensusTimestamp(),
                    a.tokenId(),
                    new byte[] {0},
                    (int) Math.ceil(restored.get(i).position()) + i,
                    a.payerAccountId(),
                    a.tokenId(),
                    true,
                    APPROVAL,
                    address(a.accountId(), addresses),
                    address(a.spender(), addresses),
                    trim(Bytes.ofUnsignedLong(a.serialNumber()).toArray()),
                    a.transactionHash() == null ? null : Arrays.copyOf(a.transactionHash(), 32),
                    transactionIndexes.get(a.consensusTimestamp())));
        }

        jdbc.batchUpdate(
                INSERT_LOG,
                inserts.stream().map(SimplePropertySqlParameterSource::new).toArray(SqlParameterSource[]::new));
        return restored.size();
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
     * The snapshot already limits the candidates to the serial approvals that can miss their log, so only those without
     * an existing Approval log are restored.
     *
     * @return the missing serial approvals grouped by consensus timestamp, in query order within each transaction
     */
    private static SortedMap<Long, List<Approval>> missingApprovals(
            final List<Approval> candidates, final List<ExistingLog> existing) {
        return candidates.stream()
                .filter(a -> existing.stream().noneMatch(log -> log.matches(a)))
                .collect(Collectors.groupingBy(Approval::consensusTimestamp, TreeMap::new, Collectors.toList()));
    }

    /**
     * Finds where the importer would have logged a serial approval among the existing logs of its transaction. The
     * importer first walks the NFT allowances, logging the ApprovalForAll of an allowance right before its serial
     * approvals, and only then logs the fungible token approvals.
     *
     * @return the fractional position between two existing block log indexes
     */
    private static double position(
            final Approval approval,
            final List<ExistingLog> existing,
            final List<BlockLog> blockLogs,
            final Map<Long, byte[]> addresses) {
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
            return anchor + 0.25;
        }

        // A delegated allowance without an ApprovalForAll log: after the other NFT logs, before the fungible ones
        if (lastNft >= 0) {
            return lastNft + 0.5;
        }

        if (first != Integer.MAX_VALUE) {
            return first - 0.5;
        }

        // Without a log in its transaction, right before the first log with a later timestamp, which was always created
        // after the approval was parsed, or else after the last log of the block
        int next = Integer.MAX_VALUE;
        int last = -1;
        for (final var log : blockLogs) {
            last = Math.max(last, log.index());
            if (log.consensusTimestamp() > approval.consensusTimestamp()) {
                next = Math.min(next, log.index());
            }
        }

        return next != Integer.MAX_VALUE ? next - 0.5 : last + 0.5;
    }

    // Returns the number of restored logs
    private int restore(final Block block) {
        final var params = new MapSqlParameterSource("start", block.consensusStart())
                .addValue("end", block.consensusEnd())
                .addValue("approval", APPROVAL)
                .addValue("approvalForAll", APPROVAL_FOR_ALL)
                .addValue("success", SUCCESS.getNumber())
                .addValue("type", TransactionType.CRYPTOAPPROVEALLOWANCE.getProtoId());
        final var jdbc = getNamedParameterJdbcOperations();
        final var timestamps = jdbc.queryForList(SELECT_APPROVAL_TIMESTAMPS, params, Long.class);
        params.addValue("timestamps", timestamps.toArray(Long[]::new));
        final var candidates = jdbc.query(SELECT_APPROVALS, params, APPROVAL_ROW_MAPPER);
        if (candidates.isEmpty()) {
            return 0;
        }

        final var existing = jdbc.query(SELECT_LOGS, params, EXISTING_LOG_ROW_MAPPER);
        final var missing = missingApprovals(candidates, existing);
        if (missing.isEmpty()) {
            return 0;
        }

        // Transaction indexes are not part of any primary key, so they are updated in place. The caller commits the
        // block changes and the migration checkpoint together, so an interrupted migration can safely retry the batch.
        final var indexes = transactionIndexes(missing, params);
        updateTransactionIndexes(params, indexes.changed());
        return insertLogs(missing, existing, indexes.missing(), params);
    }

    /**
     * Merges the transactions of the missing approvals into the block's indexed transactions. Both are ordered by
     * consensus timestamp, which is also the transaction index order. A transaction without an index is inserted before
     * the next indexed one, or after the last one, which keeps index gaps, e.g. when ingestion began partway through a
     * block. Each later transaction moves up by the number of transactions inserted before it.
     */
    private TransactionIndexes transactionIndexes(
            final SortedMap<Long, List<Approval>> missing, final SqlParameterSource params) {
        final var indexed = getNamedParameterJdbcOperations()
                .query(SELECT_TRANSACTION_INDEXES, params, INDEXED_TRANSACTION_ROW_MAPPER);
        final var missingIndexes = new HashMap<Long, Integer>();
        final var changed = new HashMap<Long, IndexChange>();
        final var timestamps = List.copyOf(missing.keySet());
        int inserted = 0;
        int next = 0;
        int pending = 0;
        for (final var transaction : indexed) {
            final long timestamp = transaction.consensusTimestamp();
            final int index = transaction.transactionIndex();
            for (; pending < timestamps.size() && timestamps.get(pending) < timestamp; pending++) {
                final int newIndex = index + inserted++;
                missingIndexes.put(timestamps.get(pending), newIndex);
                changed.put(timestamps.get(pending), new IndexChange(newIndex, true));
            }

            final int shifted = index + inserted;
            if (pending < timestamps.size() && timestamps.get(pending) == timestamp) {
                missingIndexes.put(timestamps.get(pending++), shifted);
            }

            if (inserted > 0) {
                changed.put(timestamp, new IndexChange(shifted, false));
            }

            next = index + 1;
        }

        while (pending < timestamps.size()) {
            final long timestamp = timestamps.get(pending++);
            final int newIndex = next + inserted++;
            missingIndexes.put(timestamp, newIndex);
            changed.put(timestamp, new IndexChange(newIndex, true));
        }

        return new TransactionIndexes(missingIndexes, changed);
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        return new TransactionTemplate(
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource())));
    }

    private void updateTransactionIndexes(final SqlParameterSource params, final Map<Long, IndexChange> changed) {
        if (changed.isEmpty()) {
            return;
        }

        final var timestamps = new Long[changed.size()];
        final var newIndexes = new Integer[changed.size()];
        final var inserted = new Boolean[changed.size()];
        int i = 0;
        for (final var entry : changed.entrySet()) {
            timestamps[i] = entry.getKey();
            newIndexes[i] = entry.getValue().index();
            inserted[i++] = entry.getValue().inserted();
        }

        final var updateParams = new MapSqlParameterSource("start", params.getValue("start"))
                .addValue("end", params.getValue("end"))
                .addValue("timestamps", timestamps)
                .addValue("indexes", newIndexes)
                .addValue("inserted", inserted);
        final var jdbc = getNamedParameterJdbcOperations();
        jdbc.update(UPDATE_TRANSACTION_INDEXES.formatted("contract_result"), updateParams);
        jdbc.update(UPDATE_TRANSACTION_INDEXES.formatted("contract_log"), updateParams);
    }

    private record Approval(
            long consensusTimestamp,
            long accountId,
            long spender,
            long tokenId,
            long serialNumber,
            long payerAccountId,
            byte @Nullable [] transactionHash) {}

    private record Block(long consensusStart, long consensusEnd) {}

    private record BlockLog(long consensusTimestamp, int index) {}

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

    /**
     * @param index the new transaction index
     * @param inserted whether the transaction had no index before, so all of its rows get one
     */
    private record IndexChange(int index, boolean inserted) {}

    private record IndexedTransaction(long consensusTimestamp, int transactionIndex) {}

    private record LogRow(
            byte[] bloom,
            long consensusTimestamp,
            long contractId,
            byte[] data,
            int index,
            @Nullable Long payerAccountId,
            @Nullable Long rootContractId,
            @Nullable Boolean synthetic,
            byte @Nullable [] topic0,
            byte @Nullable [] topic1,
            byte @Nullable [] topic2,
            byte @Nullable [] topic3,
            byte @Nullable [] transactionHash,
            @Nullable Integer transactionIndex) {}

    private record RestoredLog(double position, Approval approval) {}

    /**
     * @param missing the final transaction index of each transaction with missing approvals
     * @param changed the index change of each transaction whose existing rows change, by consensus timestamp
     */
    private record TransactionIndexes(Map<Long, Integer> missing, Map<Long, IndexChange> changed) {}
}
