// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.hiero.mirror.importer.migration.EvmTransactionIndexCalculator.Row;
import org.hiero.mirror.importer.migration.EvmTransactionIndexCalculator.Update;
import org.junit.jupiter.api.Test;

class EvmTransactionIndexCalculatorTest {

    private static final long HOOK_CONTRACT_ID = 365L;

    @Test
    void twoIndependentRoots() {
        var rows = List.of(row(100L, null, true, 1L, 100L, false), row(200L, null, true, 2L, 200L, false));

        assertIndices(rows, Map.of(100L, 0L, 200L, 1L));
    }

    @Test
    void childInheritsParentIndexRegardlessOfOwnGasUsed() {
        var rows = List.of(
                row(100L, null, true, 1L, 100L, false),
                row(101L, 100L, true, 1L, 0L, false), // zero-gas child, still inherits
                row(200L, null, true, 2L, 200L, false));

        assertIndices(rows, Map.of(100L, 0L, 101L, 0L, 200L, 1L));
    }

    @Test
    void rootWithZeroGasGetsNullIndexAndDoesNotShiftLaterRoots() {
        var rows = List.of(
                row(100L, null, true, 1L, 0L, false), // failed before EVM entry
                row(200L, null, true, 2L, 200L, false));

        var expected = new java.util.HashMap<Long, Long>();
        expected.put(100L, null);
        expected.put(200L, 0L);
        assertIndices(rows, expected);
    }

    @Test
    void syntheticLogOnlyRowClaimsIndexUnconditionally() {
        var rows = List.of(row(100L, null, false, 0L, 0L, true));

        assertIndices(rows, Map.of(100L, 0L));
    }

    @Test
    void nonCandidateRowIsOmittedFromResults() {
        var rows = List.of(
                row(50L, null, false, 0L, 0L, false), // plain transfer, not a candidate at all
                row(100L, null, true, 1L, 100L, false));

        var results = EvmTransactionIndexCalculator.compute(rows, HOOK_CONTRACT_ID);
        assertThat(results).extracting(Update::consensusTimestamp).containsExactly(100L);
    }

    @Test
    void scheduledTransactionWithNoParentIsIndependentRoot() {
        var rows = List.of(
                row(100L, null, true, 1L, 100L, false), // unrelated root
                row(150L, null, true, 2L, 150L, false) // scheduled execution, no parent, unrelated to the first
                );

        assertIndices(rows, Map.of(100L, 0L, 150L, 1L));
    }

    @Test
    void scheduledTransactionAsFirstCandidateInBlockGetsIndexZeroNotNegativeOne() {
        var rows = List.of(row(150L, null, true, 2L, 150L, false));

        assertIndices(rows, Map.of(150L, 0L));
    }

    @Test
    void hookChainSiblingsInheritHookIndex() {
        var rows = List.of(
                row(100L, null, false, 0L, 0L, false), // plain CryptoTransfer parent, no contract result
                row(101L, 100L, true, HOOK_CONTRACT_ID, 50L, false), // hook execution, child of the transfer
                row(102L, 100L, true, 999L, 30L, false), // sibling, also child of the transfer, not of the hook
                row(200L, null, true, 3L, 200L, false) // unrelated later root
                );

        assertIndices(rows, Map.of(101L, 0L, 102L, 0L, 200L, 1L));
    }

    @Test
    void hookChainBreaksOnNonContractSibling() {
        var rows = List.of(
                row(100L, null, false, 0L, 0L, false), // plain CryptoTransfer parent
                row(101L, 100L, true, HOOK_CONTRACT_ID, 50L, false), // hook execution
                row(102L, 100L, false, 0L, 0L, false), // unrelated sibling with no contract result - breaks chain
                row(103L, 100L, true, 999L, 30L, false) // sibling after the break - must claim its own index
                );

        assertIndices(rows, Map.of(101L, 0L, 103L, 1L));
    }

    @Test
    void hookItselfDoesNotLookUpItsOwnHookParent() {
        var rows = List.of(row(100L, null, false, 0L, 0L, false), row(101L, 100L, true, HOOK_CONTRACT_ID, 50L, false));

        assertIndices(rows, Map.of(101L, 0L));
    }

    @Test
    void hookNestedWithinContractExecutingParentInheritsFromParentNotHook() {
        var rows = List.of(
                row(100L, null, true, 1L, 500L, false), // real top-level contract call
                row(101L, 100L, true, HOOK_CONTRACT_ID, 50L, false) // hook triggered during that call's execution
                );

        assertIndices(rows, Map.of(100L, 0L, 101L, 0L));
    }

    @Test
    void secondHookExecutionInSameChainClaimsFreshIndex() {
        var rows = List.of(
                row(100L, null, false, 0L, 0L, false), // cryptoTransfer
                row(101L, 100L, true, HOOK_CONTRACT_ID, 50L, false), // hookExecution1
                row(102L, 100L, true, 999L, 30L, false), // nestedHookChild
                row(103L, 100L, true, HOOK_CONTRACT_ID, 50L, false) // hookExecution2
                );

        assertIndices(rows, Map.of(101L, 0L, 102L, 0L, 103L, 1L));
    }

    @Test
    void twoRootsEachWithChildrenGetDistinctSharedIndices() {
        var rows = List.of(
                row(100L, null, true, 1L, 1000L, false), // firstRoot
                row(101L, 100L, true, 2L, 2000L, false), // firstChild
                row(102L, null, true, 3L, 3000L, false), // secondRoot
                row(103L, 102L, true, 4L, 4000L, false), // secondChild
                row(104L, 102L, true, 5L, 5000L, false) // secondChild2, parent is secondRoot not secondChild
                );

        assertIndices(rows, Map.of(100L, 0L, 101L, 0L, 102L, 1L, 103L, 1L, 104L, 1L));
    }

    @Test
    void precompileDispatchedChildrenOfDifferentTypesInheritRootIndex() {
        var rows = List.of(
                row(100L, null, true, 1L, 1000L, false),
                row(101L, 100L, true, 1L, 500L, false),
                row(102L, 100L, true, 1L, 600L, false),
                row(103L, 100L, true, 1L, 700L, false));

        assertIndices(rows, Map.of(100L, 0L, 101L, 0L, 102L, 0L, 103L, 0L));
    }

    @Test
    void atomicBatchInnerTransactionsGetSequentialIndices() {
        var rows = List.of(
                row(100L, null, false, 0L, 0L, false), // ATOMIC_BATCH, no contract result
                row(200L, 100L, true, 1L, 500L, false), // inner CONTRACTCALL
                row(300L, 100L, true, 1L, 600L, false) // inner ETHEREUMTRANSACTION, same parent as above
                );

        assertIndices(rows, Map.of(200L, 0L, 300L, 1L));
    }

    @Test
    void multipleIndependentHookChainsInSameBlock() {
        var rows = List.of(
                row(100L, null, false, 0L, 0L, false),
                row(101L, 100L, true, HOOK_CONTRACT_ID, 50L, false),
                row(102L, 100L, true, 999L, 30L, false),
                row(300L, null, false, 0L, 0L, false),
                row(301L, 300L, true, HOOK_CONTRACT_ID, 50L, false),
                row(302L, 300L, true, 888L, 30L, false));

        assertIndices(rows, Map.of(101L, 0L, 102L, 0L, 301L, 1L, 302L, 1L));
    }

    @Test
    void unchangedRowsAreNotReturned() {
        var rows = List.of(
                stored(100L, null, true, 100L, 0L, 0L),
                stored(101L, 100L, true, 0L, 0L),
                stored(200L, null, true, 200L, 0L, 0L), // stored 0, should be 1
                stored(300L, null, true, 0L, null, (Long) null));

        var updates = EvmTransactionIndexCalculator.compute(rows, HOOK_CONTRACT_ID);

        assertThat(updates).containsExactly(new Update(200L, 1L));
    }

    @Test
    void staleLogIsUpdatedWhenContractResultIsAlreadyCorrect() {
        var rows = List.of(
                stored(100L, null, true, 100L, 0L, 0L),
                stored(200L, null, true, 200L, 1L, 0L)); // result already 1, log still 0

        var updates = EvmTransactionIndexCalculator.compute(rows, HOOK_CONTRACT_ID);

        assertThat(updates).containsExactly(new Update(200L, 1L));
    }

    @Test
    void logsWithMixedIndexesAreUpdated() {
        var rows = List.of(stored(100L, null, true, 100L, 0L, 0L, 1L));

        var updates = EvmTransactionIndexCalculator.compute(rows, HOOK_CONTRACT_ID);

        assertThat(updates).containsExactly(new Update(100L, 0L));
    }

    @Test
    void zeroGasRootLogsAreNulled() {
        var unchanged = List.of(stored(100L, null, true, 0L, null, (Long) null));
        var staleLog = List.of(stored(100L, null, true, 0L, null, 0L));

        assertThat(EvmTransactionIndexCalculator.compute(unchanged, HOOK_CONTRACT_ID))
                .isEmpty();
        assertThat(EvmTransactionIndexCalculator.compute(staleLog, HOOK_CONTRACT_ID))
                .containsExactly(new Update(100L, null));
    }

    // stored indexes that never match a computed one, so every candidate row is returned
    private static Row row(
            long consensusTimestamp,
            Long parentConsensusTimestamp,
            boolean hasContractResult,
            long contractId,
            long gasUsed,
            boolean syntheticLogOnly) {
        final var logCount = syntheticLogOnly ? 1L : 0L;
        final var logIndex = syntheticLogOnly ? -99L : null;
        return new Row(
                consensusTimestamp,
                parentConsensusTimestamp,
                hasContractResult,
                contractId,
                gasUsed,
                syntheticLogOnly,
                hasContractResult ? -99L : null,
                logCount,
                0L,
                logIndex,
                logIndex);
    }

    private static Row stored(
            long consensusTimestamp,
            Long parentConsensusTimestamp,
            boolean hasContractResult,
            long gasUsed,
            Long contractResultIndex,
            Long... logIndexes) {
        final var nonNullLogIndexes = java.util.Arrays.stream(logIndexes)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new Row(
                consensusTimestamp,
                parentConsensusTimestamp,
                hasContractResult,
                1L,
                gasUsed,
                false,
                contractResultIndex,
                logIndexes.length,
                logIndexes.length - nonNullLogIndexes.size(),
                nonNullLogIndexes.stream().min(Long::compare).orElse(null),
                nonNullLogIndexes.stream().max(Long::compare).orElse(null));
    }

    private static void assertIndices(List<Row> rows, Map<Long, Long> expected) {
        var results = EvmTransactionIndexCalculator.compute(rows, HOOK_CONTRACT_ID);
        var actual = new java.util.HashMap<Long, Long>();
        results.forEach(r -> actual.put(r.consensusTimestamp(), r.transactionIndex()));
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }
}
