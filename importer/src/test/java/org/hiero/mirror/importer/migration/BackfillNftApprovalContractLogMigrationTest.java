// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.trim;

import com.google.common.collect.Range;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class BackfillNftApprovalContractLogMigrationTest
        extends AbstractAsyncJavaMigrationTest<BackfillNftApprovalContractLogMigration> {

    private static final byte[] APPROVAL = Bytes.fromHexString(
                    "8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925")
            .toArray();
    private static final byte[] APPROVAL_FOR_ALL = Bytes.fromHexString(
                    "17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31")
            .toArray();

    private final ContractLogRepository contractLogRepository;
    private final EntityProperties entityProperties;

    @Getter
    private final BackfillNftApprovalContractLogMigration migration;

    @Test
    void beforeHapi077RestoresOnlyApprovalsWithApprovalForAllLog() {
        long start = domainBuilder.timestamp();
        block(start, 76);
        transaction(start + 100);
        transaction(start + 200);
        transaction(start + 300);
        var owner = EntityId.of(1001);
        var affected = nft(start + 100, 1, owner, 1002);
        // No ApprovalForAll log, so the Approval was not skipped by the defect, e.g. synthetic logs were not persisted
        nft(start + 200, 2, owner, 1002);
        var otherToken = nft(start + 300, 3, owner, 1002);
        var anchor = approvalForAll(start + 100, affected.getTokenId(), 0, 0);
        var unrelated = approvalForAll(start + 300, otherToken.getTokenId() + 1, 1, 1);

        runMigration();
        waitForCompletion();

        var logs = logs();
        assertThat(logs).hasSize(3);
        assertThat(logs.get(0)).isEqualTo(anchor);
        assertThat(logs.get(1)).satisfies(l -> {
            assertThat(l.getConsensusTimestamp()).isEqualTo(start + 100);
            assertThat(l.getContractId()).isEqualTo(EntityId.of(affected.getTokenId()));
            assertThat(l.getIndex()).isEqualTo(1);
            assertThat(l.getTopic0()).isEqualTo(APPROVAL);
            assertThat(l.getTopic3()).isEqualTo(new byte[] {1});
            assertThat(l.getTransactionIndex()).isZero();
        });
        unrelated.setIndex(2);
        assertThat(logs.get(2)).isEqualTo(unrelated);
    }

    @Test
    void disabled() {
        // An earlier run interrupted after creating its progress and snapshot tables
        block(domainBuilder.timestamp());
        assertThat(migration.performSynchronousSteps()).isTrue();
        migration.getInitial();
        assertThat(tableExists("nft_approval_contract_log_progress_temp")).isTrue();
        assertThat(tableExists("nft_approval_snapshot_temp")).isTrue();
        entityProperties.getPersist().setSyntheticContractLogs(false);
        try {
            assertThat(migration.performSynchronousSteps()).isFalse();
            assertThat(tableExists("nft_approval_contract_log_progress_temp")).isFalse();
            assertThat(tableExists("nft_approval_snapshot_temp")).isFalse();
        } finally {
            entityProperties.getPersist().setSyntheticContractLogs(true);
        }
    }

    @Test
    void empty() {
        runMigration();
        waitForCompletion();

        assertThat(contractLogRepository.findAll()).isEmpty();
        assertThat(tableExists("nft_approval_contract_log_progress_temp")).isFalse();
        assertThat(tableExists("nft_approval_snapshot_temp")).isFalse();
    }

    @Test
    void preservesExistingIndexOrderWhenEffectsAreLoggedAfterLaterTransactions() {
        long start = domainBuilder.timestamp();
        block(start);
        transaction(start + 200);
        nft(start + 200, 1, EntityId.of(1001), 1002);
        // The effect of the parent at start + 100 is logged after the transaction at start + 300
        var parent = log(start + 100, 0, 0);
        var later = log(start + 300, 1, 1);
        var effect = log(start + 100, 2, 0);

        runMigration();
        waitForCompletion();

        var logs = StreamSupport.stream(contractLogRepository.findAll().spliterator(), false)
                .sorted(Comparator.comparingInt(ContractLog::getIndex))
                .toList();
        assertThat(logs).hasSize(4);
        assertThat(logs.get(0)).isEqualTo(parent);
        assertThat(logs.get(1)).satisfies(l -> {
            assertThat(l.getConsensusTimestamp()).isEqualTo(start + 200);
            assertThat(l.getIndex()).isEqualTo(1);
            assertThat(l.getTopic0()).isEqualTo(APPROVAL);
            assertThat(l.getTransactionIndex()).isEqualTo(1);
        });
        later.setIndex(2);
        later.setTransactionIndex(2);
        assertThat(logs.get(2)).isEqualTo(later);
        effect.setIndex(3);
        assertThat(logs.get(3)).isEqualTo(effect);
    }

    @Test
    void preservesExistingPaddedApprovalAndUsesLongZeroAddresses() {
        long start = domainBuilder.timestamp();
        block(start);
        var transaction = transaction(start + 100);
        var owner = domainBuilder.entity().persist();
        var spender = domainBuilder.entity().persist();
        var nft = nft(transaction.getConsensusTimestamp(), 1, owner.toEntityId(), spender.getId());
        var existing = domainBuilder
                .contractLog()
                .customize(l -> l.consensusTimestamp(transaction.getConsensusTimestamp())
                        .contractId(EntityId.of(nft.getTokenId()))
                        .index(0)
                        .transactionIndex(0)
                        .topic0(APPROVAL)
                        .topic1(Bytes.wrap(DomainUtils.toEvmAddress(nft.getAccountId()))
                                .toArray())
                        .topic2(DomainUtils.toEvmAddress(EntityId.of(nft.getSpender())))
                        .topic3(Bytes.fromHexStringLenient("0x01", 32).toArray())
                        .synthetic(true))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(logs()).containsExactly(existing);
    }

    @Test
    void preservesSnapshotCutoffWhenInterruptedBeforeFirstBatch() {
        long start = domainBuilder.timestamp();
        block(start);
        transaction(start + 100);
        nft(start + 100, 1, EntityId.of(1001), 1002);
        assertThat(migration.performSynchronousSteps()).isTrue();
        long cutoff = migration.getInitial();

        long next = start + BackfillNftApprovalContractLogMigration.DEFAULT_BATCH_INTERVAL.toNanos();
        block(next);
        transaction(next + 100);
        nft(next + 100, 2, EntityId.of(1001), 1002);
        assertThat(migration.performSynchronousSteps()).isTrue();
        assertThat(migration.getInitial()).isEqualTo(cutoff);

        runMigration();
        waitForCompletion();

        assertThat(logs())
                .singleElement()
                .extracting(ContractLog::getConsensusTimestamp)
                .isEqualTo(start + 100);
        assertThat(tableExists("nft_approval_snapshot_temp")).isFalse();
    }

    @Test
    void repairsNullIndexesOnExistingLogsInTheSameTransaction() {
        long start = domainBuilder.timestamp();
        block(start);
        transaction(start + 100);
        nft(start + 100, 1, EntityId.of(1001), 1002);
        var existing = log(start + 100, 0, 0);
        jdbcOperations.update(
                "update contract_log set transaction_index = null where consensus_timestamp = ?", start + 100);
        result(start + 100, null, null);
        log(start + 200, 1, 0);

        runMigration();
        waitForCompletion();

        assertThat(logs()).extracting(ContractLog::getIndex).containsExactly(0, 1, 2);
        assertThat(logs()).extracting(ContractLog::getTransactionIndex).containsExactly(0, 0, 1);
        assertThat(logs().getFirst()).isEqualTo(existing);
        assertResultIndex(start + 100, 0);
    }

    @Test
    void restoresCurrentAndHistoricalApprovalsAndBlockIndexes() {
        long start = domainBuilder.timestamp();
        block(start);
        var first = transaction(start + 100);
        var second = transaction(start + 200);
        var owner = domainBuilder.entity().persist();
        var spender = domainBuilder.entity().persist();
        var nft = nft(first.getConsensusTimestamp(), 1, owner.toEntityId(), spender.getId());
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(nft.getAccountId())
                        .spender(nft.getSpender())
                        .tokenId(nft.getTokenId())
                        .serialNumber(2)
                        .timestampRange(Range.closedOpen(first.getConsensusTimestamp(), start + 900)))
                .persist();
        // A revision visible in both sources must still produce a single log.
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(nft.getAccountId())
                        .spender(nft.getSpender())
                        .tokenId(nft.getTokenId())
                        .serialNumber(1)
                        .timestampRange(Range.closedOpen(first.getConsensusTimestamp(), start + 800)))
                .persist();
        nft(second.getConsensusTimestamp(), 3, owner.toEntityId(), spender.getId());
        var existing = log(first.getConsensusTimestamp(), 0, 0);
        var later = log(start + 300, 1, 1);
        var child = log(start + 400, 2, 1);
        result(second.getConsensusTimestamp(), null, null);
        result(start + 300, 1, 100L);
        result(start + 400, 1, 0L);

        runMigration();
        waitForCompletion();

        var logs = logs();
        assertThat(logs).hasSize(6).extracting(ContractLog::getIndex).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(logs.get(0)).isEqualTo(existing);
        assertThat(logs.get(1)).satisfies(l -> {
            assertThat(l.getContractId()).isEqualTo(EntityId.of(nft.getTokenId()));
            assertThat(l.getRootContractId()).isEqualTo(l.getContractId());
            assertThat(l.getTopic0()).isEqualTo(APPROVAL);
            assertThat(l.getTopic1()).isEqualTo(trim(owner.getEvmAddress()));
            assertThat(l.getTopic2()).isEqualTo(trim(spender.getEvmAddress()));
            assertThat(l.getTopic3()).isEqualTo(new byte[] {1});
            assertThat(l.getData()).isEqualTo(new byte[] {0});
            assertThat(l.getBloom()).isEqualTo(new byte[] {0});
            assertThat(l.getTransactionHash()).isEqualTo(Arrays.copyOf(first.getTransactionHash(), 32));
            assertThat(l.getPayerAccountId()).isEqualTo(first.getPayerAccountId());
            assertThat(l.isSynthetic()).isTrue();
        });
        assertThat(logs.get(2).getTopic3()).isEqualTo(new byte[] {2});
        assertThat(logs).extracting(ContractLog::getTransactionIndex).containsExactly(0, 0, 0, 1, 2, 2);
        later.setIndex(4);
        later.setTransactionIndex(2);
        child.setIndex(5);
        child.setTransactionIndex(2);
        assertThat(logs.get(4)).isEqualTo(later);
        assertThat(logs.get(5)).isEqualTo(child);
        assertResultIndex(second.getConsensusTimestamp(), 1);
        assertResultIndex(start + 300, 2);
        assertResultIndex(start + 400, 2);

        runMigration();
        waitForCompletion();
        assertThat(logs()).containsExactlyElementsOf(logs);
        assertThat(tableExists("nft_approval_contract_log_progress_temp")).isFalse();
        assertThat(tableExists("nft_approval_snapshot_temp")).isFalse();
    }

    @Test
    void restoresSnapshotAfterSourceRevisionsChange() {
        long start = domainBuilder.timestamp();
        block(start);
        transaction(start + 100);
        var nft = nft(start + 100, 1, EntityId.of(1001), 1002);
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(nft.getAccountId())
                        .spender(nft.getSpender())
                        .tokenId(nft.getTokenId())
                        .serialNumber(2)
                        .timestampRange(Range.closedOpen(start + 100, start + 900)))
                .persist();
        assertThat(migration.performSynchronousSteps()).isTrue();
        migration.getInitial();

        // Later imports and retention must not change the revisions captured by the snapshot, even after restart.
        jdbcOperations.update(
                "update nft set spender = ?, timestamp_range = int8range(?, null) where token_id = ?",
                1003L,
                start + 2000,
                nft.getTokenId());
        jdbcOperations.update("delete from nft_history where token_id = ?", nft.getTokenId());

        runMigration();
        waitForCompletion();

        assertThat(logs()).hasSize(2).allSatisfy(l -> {
            assertThat(l.getConsensusTimestamp()).isEqualTo(start + 100);
            assertThat(l.getTopic0()).isEqualTo(APPROVAL);
            assertThat(l.getTopic1()).isEqualTo(trim(DomainUtils.toEvmAddress(EntityId.of(1001))));
            assertThat(l.getTopic2()).isEqualTo(trim(DomainUtils.toEvmAddress(EntityId.of(1002))));
        });
        assertThat(logs()).extracting(ContractLog::getTopic3).containsExactly(new byte[] {1}, new byte[] {2});
    }

    @Test
    void resumesAcrossBlocksAndAssignsIndexesToPreviouslyLoglessTransactions() {
        long start = domainBuilder.timestamp();
        block(start);
        transaction(start + 100);
        transaction(start + 200);
        nft(start + 100, 1, EntityId.of(1001), 1002);
        nft(start + 200, 2, EntityId.of(1001), 1002);
        // Scheduled HAPI approvals also claim their own index.
        jdbcOperations.update(
                "update transaction set nonce = 1, scheduled = true where consensus_timestamp = ?", start + 200);
        long next = start + BackfillNftApprovalContractLogMigration.DEFAULT_BATCH_INTERVAL.toNanos() * 2;
        block(next);
        transaction(next + 100);
        nft(next + 100, 3, EntityId.of(1001), 1002);
        assertThat(migration.performSynchronousSteps()).isTrue();
        migration
                .getTransactionOperations()
                .executeWithoutResult(_ -> migration.migratePartial(migration.getInitial()));
        assertThat(logs()).hasSize(1);

        runMigration();
        waitForCompletion();

        assertThat(logs()).extracting(ContractLog::getIndex).containsExactly(0, 1, 0);
        assertThat(logs()).extracting(ContractLog::getTransactionIndex).containsExactly(0, 1, 0);
        assertThat(logs().getFirst().getTopic1()).isEqualTo(trim(DomainUtils.toEvmAddress(EntityId.of(1001))));
    }

    @Test
    void skipsFailedContractOriginAndOtherTransactions() {
        long start = domainBuilder.timestamp();
        block(start);
        for (int i = 1; i <= 4; i++) {
            long timestamp = start + i * 100;
            transaction(timestamp);
            nft(timestamp, i, EntityId.of(1001), 1002);
        }
        jdbcOperations.update("update transaction set result = 1 where consensus_timestamp = ?", start + 100);
        result(start + 200, 0, 0L); // Consensus produced the contract result, even with zero gas used.
        jdbcOperations.update("update transaction set nonce = 1 where consensus_timestamp = ?", start + 300);
        jdbcOperations.update(
                "update transaction set type = ? where consensus_timestamp = ?",
                TransactionType.CRYPTOTRANSFER.getProtoId(),
                start + 400);

        runMigration();
        waitForCompletion();

        assertThat(logs()).isEmpty();
    }

    private ContractLog approvalForAll(long timestamp, long tokenId, int index, int transactionIndex) {
        return domainBuilder
                .contractLog()
                .customize(l -> l.consensusTimestamp(timestamp)
                        .contractId(EntityId.of(tokenId))
                        .rootContractId(EntityId.of(tokenId))
                        .index(index)
                        .transactionIndex(transactionIndex)
                        .topic0(APPROVAL_FOR_ALL)
                        .topic3(null)
                        .synthetic(true))
                .persist();
    }

    private void assertResultIndex(long timestamp, int expected) {
        assertThat(jdbcOperations.queryForObject(
                        "select transaction_index from contract_result where consensus_timestamp = ?",
                        Integer.class,
                        timestamp))
                .isEqualTo(expected);
    }

    private void block(long start) {
        block(start, 77);
    }

    private void block(long start, int hapiVersionMinor) {
        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(start)
                        .consensusEnd(start + 1000)
                        .hapiVersionMajor(0)
                        .hapiVersionMinor(hapiVersionMinor)
                        .hapiVersionPatch(0))
                .persist();
    }

    private ContractLog log(long timestamp, int index, int transactionIndex) {
        return domainBuilder
                .contractLog()
                .customize(l -> l.consensusTimestamp(timestamp).index(index).transactionIndex(transactionIndex))
                .persist();
    }

    private List<ContractLog> logs() {
        return StreamSupport.stream(contractLogRepository.findAll().spliterator(), false)
                .sorted(Comparator.comparingLong(ContractLog::getConsensusTimestamp)
                        .thenComparingInt(ContractLog::getIndex))
                .toList();
    }

    private Nft nft(long timestamp, long serial, EntityId owner, long spender) {
        return domainBuilder
                .nft()
                .customize(n -> n.accountId(owner)
                        .spender(spender)
                        .serialNumber(serial)
                        .timestampRange(Range.atLeast(timestamp)))
                .persist();
    }

    private void result(long timestamp, Integer index, Long gasUsed) {
        domainBuilder
                .contractResult()
                .customize(r -> r.consensusTimestamp(timestamp)
                        .transactionIndex(index)
                        .gasUsed(gasUsed)
                        .child(false))
                .persist();
    }

    private Transaction transaction(long timestamp) {
        return domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp)
                        .type(TransactionType.CRYPTOAPPROVEALLOWANCE.getProtoId())
                        .result(SUCCESS.getNumber())
                        .nonce(0)
                        .parentConsensusTimestamp(null)
                        .scheduled(false))
                .persist();
    }
}
