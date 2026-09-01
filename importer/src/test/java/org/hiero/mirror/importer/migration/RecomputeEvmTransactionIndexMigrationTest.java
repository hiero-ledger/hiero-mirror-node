// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Duration;
import java.util.ArrayList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class RecomputeEvmTransactionIndexMigrationTest
        extends AbstractAsyncJavaMigrationTest<RecomputeEvmTransactionIndexMigration> {

    private static final long INTERVAL = Duration.ofHours(3).toNanos();

    @Getter
    private final RecomputeEvmTransactionIndexMigration migration;

    private final ContractLogRepository contractLogRepository;
    private final ContractResultRepository contractResultRepository;
    private final RecordFileRepository recordFileRepository;

    @Test
    void emptyDatabase() {
        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(recordFileRepository.findAll()).isEmpty();
        assertThat(contractResultRepository.findAll()).isEmpty();
        assertThat(contractLogRepository.findAll()).isEmpty();
    }

    @Test
    void dropsOldProgressTables() {
        // given
        persistBlock(0);
        ownerJdbcTemplate.execute(
                "create table if not exists fix_evm_transaction_index_progress_temp(upper_bound bigint not null)");
        ownerJdbcTemplate.execute(
                "create table if not exists contract_log_synthetic_flag_progress_temp(upper_bound bigint not null)");
        ownerJdbcTemplate.execute(
                "create table if not exists contract_log_synthetic_progress_temp(upper_bound bigint not null)");

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(tableExists("fix_evm_transaction_index_progress_temp")).isFalse();
        assertThat(tableExists("contract_log_synthetic_flag_progress_temp")).isFalse();
        assertThat(tableExists("contract_log_synthetic_progress_temp")).isFalse();
    }

    @Test
    void syntheticFlagBackfilledForLogsWithoutMatchingContractResult() {
        // given
        final var block = persistBlock(0);
        final var timestamp = block.getConsensusStart() + 100;
        final var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(timestamp).synthetic(false))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(findSyntheticFlag(contractLog.getConsensusTimestamp())).isTrue();
    }

    @Test
    void syntheticFlagPreservedWhenContractResultExistsAtSameTimestamp() {
        // given
        final var block = persistBlock(0);
        final var timestamp = block.getConsensusStart() + 100;
        final var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(timestamp).synthetic(false))
                .persist();
        domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(timestamp))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(findSyntheticFlag(contractLog.getConsensusTimestamp())).isFalse();
    }

    @Test
    void syntheticFlagBackfilledWhenContractResultAtDifferentTimestamp() {
        // given
        final var block = persistBlock(0);
        final var logTimestamp = block.getConsensusStart() + 100;
        final var resultTimestamp = block.getConsensusStart() + 200;
        final var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(logTimestamp).synthetic(false))
                .persist();
        domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(resultTimestamp))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(findSyntheticFlag(contractLog.getConsensusTimestamp())).isTrue();
    }

    @Test
    void alreadyTrueSyntheticFlagStaysUnchanged() {
        // given
        final var block = persistBlock(0);
        final var timestamp = block.getConsensusStart() + 100;
        final var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(timestamp).synthetic(true))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(findSyntheticFlag(contractLog.getConsensusTimestamp())).isTrue();
    }

    @Test
    void nullIndexGetsUpdatedToRealIndex() {
        // given
        final var block = persistBlock(0);
        final var timestamp = block.getConsensusStart() + 100;
        persistTransaction(timestamp, TransactionType.CONTRACTCALL, 0, false, null);

        final var contractResult = persistContractResult(timestamp, 0, null);
        final var contractLog = persistContractLog(timestamp, null);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(contractResult.getConsensusTimestamp(), 0);
        assertContractLogIndex(contractLog.getConsensusTimestamp(), 0);
    }

    @Test
    void topLevelEvmTransactionsGetSequentialIndices() {
        // given
        final var block = persistBlock(0);
        final var contractCallTimestamp = block.getConsensusStart() + 100;
        final var contractCreateTimestamp = block.getConsensusStart() + 200;
        final var ethereumTxTimestamp = block.getConsensusStart() + 300;

        persistTransaction(contractCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(contractCreateTimestamp, TransactionType.CONTRACTCREATEINSTANCE, 0, false, null);
        persistTransaction(ethereumTxTimestamp, TransactionType.ETHEREUMTRANSACTION, 0, false, null);

        final var contractCallResult = persistContractResult(contractCallTimestamp, 0);
        final var contractCreateResult = persistContractResult(contractCreateTimestamp, 1);
        final var ethereumTxResult = persistContractResult(ethereumTxTimestamp, 2);
        final var contractCallLog = persistContractLog(contractCallTimestamp, 5);
        final var ethereumTxLog = persistContractLog(ethereumTxTimestamp, 7);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(contractCreateResult.getConsensusTimestamp(), 1);
        assertContractResultIndex(ethereumTxResult.getConsensusTimestamp(), 2);
        assertContractLogIndex(contractCallLog.getConsensusTimestamp(), 0);
        assertContractLogIndex(ethereumTxLog.getConsensusTimestamp(), 2);
    }

    @Test
    void scheduledEvmTransactionGetsOwnIndex() {
        // given
        final var block = persistBlock(0);
        final var scheduledCallTimestamp = block.getConsensusStart() + 100;

        persistTransaction(scheduledCallTimestamp, TransactionType.CONTRACTCALL, 0, true, null);

        final var contractResult = persistContractResult(scheduledCallTimestamp, 99);
        final var contractLog = persistContractLog(scheduledCallTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(contractResult.getConsensusTimestamp(), 0);
        assertContractLogIndex(contractLog.getConsensusTimestamp(), 0);
    }

    @Test
    void evmChildInheritsParentIndex() {
        // given
        final var block = persistBlock(0);
        final var cryptoTransferTimestamp = block.getConsensusStart() + 100;
        final var parentCallTimestamp = block.getConsensusStart() + 200;
        final var childCreateTimestamp = block.getConsensusStart() + 300;

        persistTransaction(cryptoTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, null);
        persistTransaction(parentCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(childCreateTimestamp, TransactionType.CONTRACTCREATEINSTANCE, 1, false, parentCallTimestamp);

        final var parentContractResult = persistContractResult(parentCallTimestamp, 99);
        final var childContractResult = persistContractResult(childCreateTimestamp, 99);
        final var childContractLog = persistContractLog(childCreateTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(parentContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(childContractResult.getConsensusTimestamp(), 0);
        assertContractLogIndex(childContractLog.getConsensusTimestamp(), 0);
    }

    @Test
    void twoRootsEachWithChildrenGetDistinctSharedIndices() {
        // given
        final var block = persistBlock(0);
        final var firstRootTimestamp = block.getConsensusStart() + 100;
        final var firstChildTimestamp = block.getConsensusStart() + 200;
        final var secondRootTimestamp = block.getConsensusStart() + 300;
        final var secondChildTimestamp = block.getConsensusStart() + 400;
        final var secondChild2Timestamp = block.getConsensusStart() + 500;

        persistTransaction(firstRootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(firstChildTimestamp, TransactionType.CONTRACTCREATEINSTANCE, 1, false, firstRootTimestamp);
        persistTransaction(secondRootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(secondChildTimestamp, TransactionType.CONTRACTCREATEINSTANCE, 1, false, secondRootTimestamp);
        persistTransaction(
                secondChild2Timestamp, TransactionType.CONTRACTCREATEINSTANCE, 2, false, secondRootTimestamp);

        final var firstRootResult = persistContractResult(firstRootTimestamp, 99);
        final var firstChildResult = persistContractResult(firstChildTimestamp, 99);
        final var secondRootResult = persistContractResult(secondRootTimestamp, 99);
        final var secondChildResult = persistContractResult(secondChildTimestamp, 99);
        final var secondChild2Result = persistContractResult(secondChild2Timestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(firstRootResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(firstChildResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(secondRootResult.getConsensusTimestamp(), 1);
        assertContractResultIndex(secondChildResult.getConsensusTimestamp(), 1);
        assertContractResultIndex(secondChild2Result.getConsensusTimestamp(), 1);
    }

    @Test
    void precompileDispatchedChildrenOfDifferentTypesInheritRootIndex() {
        // given
        // root CONTRACTCALL nonce 0 dispatches precompile-triggered
        // TOKENASSOCIATE/TOKENMINT/CRYPTOTRANSFER children at nonce 1-3, each with its own contract_result
        final var block = persistBlock(0);
        final var rootTimestamp = block.getConsensusStart() + 100;
        final var tokenAssociateTimestamp = block.getConsensusStart() + 200;
        final var tokenMintTimestamp = block.getConsensusStart() + 300;
        final var cryptoTransferTimestamp = block.getConsensusStart() + 400;

        persistTransaction(rootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(tokenAssociateTimestamp, TransactionType.TOKENASSOCIATE, 1, false, rootTimestamp);
        persistTransaction(tokenMintTimestamp, TransactionType.TOKENMINT, 2, false, rootTimestamp);
        persistTransaction(cryptoTransferTimestamp, TransactionType.CRYPTOTRANSFER, 3, false, rootTimestamp);

        final var rootResult = persistContractResult(rootTimestamp, 99);
        final var tokenAssociateResult = persistContractResult(tokenAssociateTimestamp, 99);
        final var tokenMintResult = persistContractResult(tokenMintTimestamp, 99);
        final var cryptoTransferResult = persistContractResult(cryptoTransferTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(rootResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(tokenAssociateResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(tokenMintResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(cryptoTransferResult.getConsensusTimestamp(), 0);
    }

    @Test
    void deeplyNestedEvmTransactionsInheritRootIndexAtAnyDepth() {
        // given
        final var block = persistBlock(0);
        final var rootTimestamp = block.getConsensusStart() + 100;
        persistTransaction(rootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        final var rootContractResult = persistContractResult(rootTimestamp, 99);

        final var depth = 10;
        var parentTimestamp = rootTimestamp;
        final var descendantResults = new ArrayList<ContractResult>();
        final var descendantLogs = new ArrayList<ContractLog>();
        for (int level = 1; level <= depth; level++) {
            final var timestamp = block.getConsensusStart() + 100 + (level * 100L);
            persistTransaction(timestamp, TransactionType.CONTRACTCALL, level, false, parentTimestamp);
            descendantResults.add(persistContractResult(timestamp, 99));
            descendantLogs.add(persistContractLog(timestamp, 99));
            parentTimestamp = timestamp;
        }

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(rootContractResult.getConsensusTimestamp(), 0);
        descendantResults.forEach(result -> assertContractResultIndex(result.getConsensusTimestamp(), 0));
        descendantLogs.forEach(log -> assertContractLogIndex(log.getConsensusTimestamp(), 0));
    }

    @Test
    void atomicBatchInnerTransactionsGetSequentialIndices() {
        // given
        final var block = persistBlock(0);
        final var batchTimestamp = block.getConsensusStart() + 100;
        final var innerCallTimestamp = block.getConsensusStart() + 200;
        final var innerEthereumTimestamp = block.getConsensusStart() + 300;

        persistTransaction(batchTimestamp, TransactionType.ATOMIC_BATCH, 0, false, null);
        persistTransaction(innerCallTimestamp, TransactionType.CONTRACTCALL, 0, false, batchTimestamp);
        persistTransaction(innerEthereumTimestamp, TransactionType.ETHEREUMTRANSACTION, 0, false, batchTimestamp);

        final var innerCallResult = persistContractResult(innerCallTimestamp, 99);
        final var innerEthereumResult = persistContractResult(innerEthereumTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(innerCallResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(innerEthereumResult.getConsensusTimestamp(), 1);
    }

    @Test
    void hookEvmChildGetsOwnIndex() {
        // given
        final var block = persistBlock(0);
        final var cryptoTransferTimestamp = block.getConsensusStart() + 100;
        final var hookCall1Timestamp = block.getConsensusStart() + 200;
        final var hookCall2Timestamp = block.getConsensusStart() + 300;

        persistTransaction(cryptoTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, null);
        persistHookDispatchTransaction(hookCall1Timestamp, 1, cryptoTransferTimestamp);
        persistHookDispatchTransaction(hookCall2Timestamp, 2, cryptoTransferTimestamp);

        final var hookContractResult1 = persistHookDispatchContractResult(hookCall1Timestamp, 42);
        final var hookContractLog1 = persistContractLog(hookCall1Timestamp, 42);
        final var hookContractResult2 = persistHookDispatchContractResult(hookCall2Timestamp, 43);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(hookContractResult1.getConsensusTimestamp(), 0);
        assertContractLogIndex(hookContractLog1.getConsensusTimestamp(), 0);
        assertContractResultIndex(hookContractResult2.getConsensusTimestamp(), 1);
    }

    @Test
    void nestedHookEvmTransactionInheritsHookIndex() {
        // given
        final var block = persistBlock(0);
        final var cryptoTransferTimestamp = block.getConsensusStart() + 100;
        final var hookCallTimestamp = block.getConsensusStart() + 200;
        final var nestedHookCallTimestamp = block.getConsensusStart() + 300;

        persistTransaction(cryptoTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, null);
        persistHookDispatchTransaction(hookCallTimestamp, 1, cryptoTransferTimestamp);
        persistTransaction(nestedHookCallTimestamp, TransactionType.CONTRACTCALL, 2, false, hookCallTimestamp);

        final var hookContractResult = persistHookDispatchContractResult(hookCallTimestamp, 42);
        final var nestedHookContractResult = persistContractResult(nestedHookCallTimestamp, 43);
        final var nestedHookContractLog = persistContractLog(nestedHookCallTimestamp, 43);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(hookContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(nestedHookContractResult.getConsensusTimestamp(), 0);
        assertContractLogIndex(nestedHookContractLog.getConsensusTimestamp(), 0);
    }

    @Test
    void hookDescendantSharingOriginalTriggerInheritsHookIndex() {
        // given
        final var block = persistBlock(0);
        final var cryptoTransferTimestamp = block.getConsensusStart() + 100;
        final var hookCallTimestamp = block.getConsensusStart() + 200;
        final var hookInternalCallTimestamp = block.getConsensusStart() + 300;
        final var unrelatedTopLevelCallTimestamp = block.getConsensusStart() + 400;

        persistTransaction(cryptoTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, null);
        persistHookDispatchTransaction(hookCallTimestamp, 1, cryptoTransferTimestamp);
        persistTransaction(hookInternalCallTimestamp, TransactionType.CONTRACTCALL, 2, false, cryptoTransferTimestamp);
        persistTransaction(unrelatedTopLevelCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);

        final var hookContractResult = persistHookDispatchContractResult(hookCallTimestamp, 42);
        final var hookInternalContractResult = persistContractResult(hookInternalCallTimestamp, 43);
        final var hookInternalContractLog = persistContractLog(hookInternalCallTimestamp, 43);
        final var unrelatedContractResult = persistContractResult(unrelatedTopLevelCallTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(hookContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(hookInternalContractResult.getConsensusTimestamp(), 0);
        assertContractLogIndex(hookInternalContractLog.getConsensusTimestamp(), 0);
        assertContractResultIndex(unrelatedContractResult.getConsensusTimestamp(), 1);
    }

    @Test
    void skipsNonEvmTransactions() {
        // given
        final var block = persistBlock(0);
        persistTransaction(block.getConsensusStart() + 100, TransactionType.CRYPTOTRANSFER, 0, false, null);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(contractResultRepository.findAll()).isEmpty();
    }

    @Test
    void failedEvmRootWithoutContractResultDoesNotConsumeIndex() {
        // given
        final var block = persistBlock(0);
        final var failedEthereumTimestamp = block.getConsensusStart() + 100;
        final var successfulCallTimestamp = block.getConsensusStart() + 200;

        persistTransaction(failedEthereumTimestamp, TransactionType.ETHEREUMTRANSACTION, 0, false, null);
        persistTransaction(successfulCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);

        final var contractCallResult = persistContractResult(successfulCallTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 0);
    }

    @Test
    void indicesResetPerBlock() {
        // given
        final var firstBlock = persistBlock(0);
        final var secondBlock = persistBlock(1);
        final var firstTimestamp = firstBlock.getConsensusStart() + 100;
        final var secondTimestamp = secondBlock.getConsensusStart() + 100;

        persistTransaction(firstTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(secondTimestamp, TransactionType.CONTRACTCALL, 0, false, null);

        final var firstContractResult = persistContractResult(firstTimestamp, 99);
        final var secondContractResult = persistContractResult(secondTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(firstContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(secondContractResult.getConsensusTimestamp(), 0);
    }

    @Test
    void processesMultipleBatchIntervals() {
        // given
        final var earlyBlock = persistBlock(0);
        final var recentBase =
                earlyBlock.getConsensusEnd() + INTERVAL - Duration.ofSeconds(1).toNanos();
        final var recentBlock = domainBuilder
                .recordFile()
                .customize(r -> r.index(1L)
                        .consensusStart(recentBase)
                        .consensusEnd(recentBase + Duration.ofSeconds(2).toNanos()))
                .persist();

        final var earlyTimestamp = earlyBlock.getConsensusStart() + 100;
        final var recentTimestamp = recentBlock.getConsensusStart() + 100;

        persistTransaction(earlyTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(recentTimestamp, TransactionType.ETHEREUMTRANSACTION, 0, false, null);

        final var earlyContractResult = persistContractResult(earlyTimestamp, 99);
        final var recentContractResult = persistContractResult(recentTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(earlyContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(recentContractResult.getConsensusTimestamp(), 0);
    }

    @Test
    void missedTransactions() {
        // given
        final var block1 = persistBlock(1);
        final var timestamp = block1.getConsensusStart() - INTERVAL * 2;
        final var block0 = domainBuilder
                .recordFile()
                .customize(r -> r.index(0L)
                        .consensusStart(timestamp)
                        .consensusEnd(timestamp + Duration.ofSeconds(2).toNanos()))
                .persist();

        final var earlyTimestamp = block0.getConsensusStart() + 100;
        persistTransaction(earlyTimestamp, TransactionType.CONTRACTCALL, 0, false, null);

        final var earlyContractResult = persistContractResult(earlyTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(earlyContractResult.getConsensusTimestamp(), 0);
    }

    @Test
    void singleTransactionInIntervalNotInfiniteLoop() {
        // given
        final long timestamp1 = domainBuilder.timestamp();
        final var block1 = domainBuilder
                .recordFile()
                .customize(r -> r.index(1L).consensusStart(timestamp1).consensusEnd(timestamp1))
                .persist();
        final long timestamp0 = block1.getConsensusEnd() - INTERVAL + 1;
        domainBuilder
                .recordFile()
                .customize(r -> r.index(0L).consensusStart(timestamp0).consensusEnd(timestamp0))
                .persist();

        // when
        runMigration();
        waitForCompletion();
    }

    @Test
    void syntheticContractLogFromHapiOperationGetsEvmIndex() {
        // given
        final var block = persistBlock(0);
        final var tokenTransferTimestamp = block.getConsensusStart() + 100;
        final var contractCallTimestamp = block.getConsensusStart() + 200;

        persistTransaction(tokenTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, null);
        persistTransaction(contractCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);

        final var syntheticLog = persistSyntheticContractLog(tokenTransferTimestamp, 999);
        final var contractCallResult = persistContractResult(contractCallTimestamp, 99);
        final var contractCallLog = persistContractLog(contractCallTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractLogIndex(syntheticLog.getConsensusTimestamp(), 0);
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 1);
        assertContractLogIndex(contractCallLog.getConsensusTimestamp(), 1);
    }

    @Test
    void zeroGasUsedDoesNotConsumeEvmIndex() {
        // given
        final var block = persistBlock(0);
        final var wrongNonceTimestamp = block.getConsensusStart() + 100;
        final var contractCallTimestamp = block.getConsensusStart() + 200;

        final var wrongNonceResult =
                persistContractResult(wrongNonceTimestamp, 0, 99, ResponseCodeEnum.WRONG_NONCE_VALUE, 0L);
        final var contractCallResult = persistContractResult(contractCallTimestamp, 0, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(wrongNonceResult.getConsensusTimestamp(), null);
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 0);
    }

    @Test
    void nullGasUsedFailedContractCreateIsNulledOut() {
        // given
        // Failed contract create (e.g. INSUFFICIENT_PAYER_BALANCE) can have a null gasUsed, not just zero
        final var block = persistBlock(0);
        final var failedCreateTimestamp = block.getConsensusStart() + 100;
        final var contractCallTimestamp = block.getConsensusStart() + 200;

        final var failedCreateResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(failedCreateTimestamp)
                        .transactionIndex(99)
                        .transactionNonce(0)
                        .gasUsed(null)
                        .transactionResult(ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_VALUE))
                .persist();
        final var contractCallResult = persistContractResult(contractCallTimestamp, 0, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(failedCreateResult.getConsensusTimestamp(), null);
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 0);
    }

    @Test
    void zeroGasUsedContractLogIndexIsNulledOut() {
        // given
        // Simulates a stale wrong index
        final var block = persistBlock(0);
        final var zeroGasTimestamp = block.getConsensusStart() + 100;
        final var contractCallTimestamp = block.getConsensusStart() + 200;

        final var zeroGasResult =
                persistContractResult(zeroGasTimestamp, 0, 5, ResponseCodeEnum.INVALID_SIGNATURE_VALUE, 0L);
        final var zeroGasLog = persistContractLog(zeroGasTimestamp, 5);
        final var contractCallResult = persistContractResult(contractCallTimestamp, 0, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(zeroGasResult.getConsensusTimestamp(), null);
        assertContractLogIndex(zeroGasLog.getConsensusTimestamp(), null);
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 0);
    }

    @Test
    void zeroGasUsedExcludesRegardlessOfStatus() {
        // given
        // Not on the old status-based exclude list
        final var block = persistBlock(0);
        final var contractRevertTimestamp = block.getConsensusStart() + 100;
        final var contractCallTimestamp = block.getConsensusStart() + 200;

        final var contractRevertResult = persistContractResult(
                contractRevertTimestamp, 0, 99, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED_VALUE, 0L);
        final var contractCallResult = persistContractResult(contractCallTimestamp, 0, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(contractRevertResult.getConsensusTimestamp(), null);
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 0);
    }

    @Test
    void positiveGasUsedConsumesEvmIndexRegardlessOfStatus() {
        // given
        // INVALID_ACCOUNT_ID has multiple origins
        final var block = persistBlock(0);
        final var invalidAccountIdTimestamp = block.getConsensusStart() + 100;
        final var contractCallTimestamp = block.getConsensusStart() + 200;

        final var invalidAccountIdResult =
                persistContractResult(invalidAccountIdTimestamp, 0, 99, ResponseCodeEnum.INVALID_ACCOUNT_ID_VALUE);
        final var contractCallResult = persistContractResult(contractCallTimestamp, 0, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(invalidAccountIdResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 1);
    }

    @Test
    void zeroGasUsedChildStillInheritsParentIndex() {
        // given
        // Real precompile children can have gasUsed=0
        final var block = persistBlock(0);
        final var rootTimestamp = block.getConsensusStart() + 100;
        final var childTimestamp = block.getConsensusStart() + 200;

        final var rootResult = persistContractResult(rootTimestamp, 0, 99);
        final var childResult = persistContractResult(childTimestamp, 1, 99, ResponseCodeEnum.SUCCESS_VALUE, 0L);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(rootResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(childResult.getConsensusTimestamp(), 0);
    }

    @Test
    void doesNotDoubleCountEvmIndexWhenSyntheticLogSharesTimestampWithContractResult() {
        // given
        final var block = persistBlock(0);
        final var sharedTimestamp = block.getConsensusStart() + 100;
        final var laterTimestamp = block.getConsensusStart() + 200;

        final var sharedContractResult = persistContractResult(sharedTimestamp, 0, 99);
        persistSyntheticContractLog(sharedTimestamp, 99);
        final var laterContractResult = persistContractResult(laterTimestamp, 0, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(sharedContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(laterContractResult.getConsensusTimestamp(), 1);
    }

    private RecordFile persistBlock(long index) {
        final var base = domainBuilder.timestamp() + index * INTERVAL;
        return domainBuilder
                .recordFile()
                .customize(r -> r.index(index)
                        .consensusStart(base)
                        .consensusEnd(base + Duration.ofSeconds(2).toNanos()))
                .persist();
    }

    private void persistTransaction(
            long consensusTimestamp,
            TransactionType type,
            int nonce,
            boolean scheduled,
            Long parentConsensusTimestamp) {
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)
                        .type(type.getProtoId())
                        .nonce(nonce)
                        .scheduled(scheduled)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .entityId(null))
                .persist();
    }

    private void persistHookDispatchTransaction(long consensusTimestamp, int nonce, Long parentConsensusTimestamp) {
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)
                        .type(TransactionType.CONTRACTCALL.getProtoId())
                        .nonce(nonce)
                        .scheduled(false)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .entityId(EntityId.of(0L, 0L, RecordItem.HOOK_CONTRACT_NUM)))
                .persist();
    }

    private ContractResult persistContractResult(long consensusTimestamp, Integer wrongIndex) {
        final var nonce = jdbcOperations.queryForObject(
                "select nonce from transaction where consensus_timestamp = ?", Integer.class, consensusTimestamp);
        return domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .transactionIndex(wrongIndex)
                        .transactionNonce(nonce != null ? nonce : 0))
                .persist();
    }

    private ContractResult persistContractResult(long consensusTimestamp, int nonce, Integer existingIndex) {
        return persistContractResult(consensusTimestamp, nonce, existingIndex, ResponseCodeEnum.SUCCESS_VALUE);
    }

    private ContractResult persistContractResult(
            long consensusTimestamp, int nonce, Integer existingIndex, int transactionResult) {
        return persistContractResult(consensusTimestamp, nonce, existingIndex, transactionResult, 100L);
    }

    private ContractResult persistContractResult(
            long consensusTimestamp, int nonce, Integer existingIndex, int transactionResult, long gasUsed) {
        return domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .transactionIndex(existingIndex)
                        .transactionNonce(nonce)
                        .gasUsed(gasUsed)
                        .transactionResult(transactionResult))
                .persist();
    }

    private ContractResult persistHookDispatchContractResult(long consensusTimestamp, int wrongIndex) {
        return domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(EntityId.of(0L, 0L, RecordItem.HOOK_CONTRACT_NUM)
                                .getId())
                        .transactionIndex(wrongIndex))
                .persist();
    }

    private ContractLog persistContractLog(long consensusTimestamp, Integer wrongIndex) {
        return domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp).transactionIndex(wrongIndex))
                .persist();
    }

    private ContractLog persistSyntheticContractLog(long consensusTimestamp, int wrongIndex) {
        return domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .transactionIndex(wrongIndex)
                        .synthetic(true))
                .persist();
    }

    private void assertContractResultIndex(long consensusTimestamp, Integer expected) {
        assertThat(jdbcOperations.queryForObject(
                        "select transaction_index from contract_result where consensus_timestamp = ?",
                        Integer.class,
                        consensusTimestamp))
                .isEqualTo(expected);
    }

    private Boolean findSyntheticFlag(long consensusTimestamp) {
        return jdbcOperations.queryForObject(
                "select synthetic from contract_log where consensus_timestamp = ?", Boolean.class, consensusTimestamp);
    }

    private void assertContractLogIndex(long consensusTimestamp, Integer expected) {
        assertThat(jdbcOperations.queryForObject(
                        "select transaction_index from contract_log where consensus_timestamp = ?",
                        Integer.class,
                        consensusTimestamp))
                .isEqualTo(expected);
    }
}
