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
        var rows = List.of(
                new Row(100L, null, true, 1L, 100L, false, false, 0),
                new Row(200L, null, true, 2L, 200L, false, false, 0));

        assertIndices(rows, Map.of(100L, 0L, 200L, 1L));
    }

    @Test
    void childInheritsParentIndexRegardlessOfOwnGasUsed() {
        var rows = List.of(
                new Row(100L, null, true, 1L, 100L, false, false, 0),
                new Row(101L, 100L, true, 1L, 0L, false, false, 1), // zero-gas child, still inherits
                new Row(200L, null, true, 2L, 200L, false, false, 0));

        assertIndices(rows, Map.of(100L, 0L, 101L, 0L, 200L, 1L));
    }

    @Test
    void rootWithZeroGasGetsNullIndexAndDoesNotShiftLaterRoots() {
        var rows = List.of(
                new Row(100L, null, true, 1L, 0L, false, false, 0), // failed before EVM entry
                new Row(200L, null, true, 2L, 200L, false, false, 0));

        var expected = new java.util.HashMap<Long, Long>();
        expected.put(100L, null);
        expected.put(200L, 0L);
        assertIndices(rows, expected);
    }

    @Test
    void childrenOfRootWithoutIndexInheritNullAndDoNotShiftLaterRoots() {
        var rows = List.of(
                new Row(100L, null, true, 1L, 0L, false, false, 0), // failed call, no index
                new Row(101L, 100L, true, 359L, 14000L, false, false, 1), // precompile effects
                new Row(102L, 100L, true, 359L, 13000L, false, false, 1),
                new Row(200L, null, true, 2L, 200L, false, false, 0));

        var expected = new java.util.HashMap<Long, Long>();
        expected.put(100L, null);
        expected.put(101L, null);
        expected.put(102L, null);
        expected.put(200L, 0L);
        assertIndices(rows, expected);
    }

    @Test
    void scheduledCallEffectsInheritScheduledCallIndex() {
        // the effects are parented to the ScheduleSign, not to the scheduled call
        var rows = List.of(
                new Row(100L, null, false, 0L, 0L, false, false, 0), // ScheduleSign
                new Row(101L, null, true, 1L, 227547L, false, true, 0), // scheduled call
                new Row(102L, 100L, true, 359L, 15284L, false, false, 1),
                new Row(103L, 100L, true, 359L, 2607L, false, false, 1),
                new Row(200L, null, true, 2L, 200L, false, false, 0));

        assertIndices(rows, Map.of(101L, 0L, 102L, 0L, 103L, 0L, 200L, 1L));
    }

    @Test
    void syntheticLogOnlyRowClaimsIndexUnconditionally() {
        var rows = List.of(new Row(100L, null, false, 0L, 0L, true, false, 0));

        assertIndices(rows, Map.of(100L, 0L));
    }

    @Test
    void nonCandidateRowIsOmittedFromResults() {
        var rows = List.of(
                new Row(50L, null, false, 0L, 0L, false, false, 0), // plain transfer, not a candidate at all
                new Row(100L, null, true, 1L, 100L, false, false, 0));

        var results = EvmTransactionIndexCalculator.compute(rows, HOOK_CONTRACT_ID);
        assertThat(results).extracting(Update::consensusTimestamp).containsExactly(100L);
    }

    @Test
    void scheduledTransactionWithNoParentIsIndependentRoot() {
        var rows = List.of(
                new Row(100L, null, true, 1L, 100L, false, false, 0), // unrelated root
                new Row(
                        150L, null, true, 2L, 150L, false, false,
                        0) // scheduled execution, no parent, unrelated to the first
                );

        assertIndices(rows, Map.of(100L, 0L, 150L, 1L));
    }

    @Test
    void scheduledTransactionAsFirstCandidateInBlockGetsIndexZeroNotNegativeOne() {
        var rows = List.of(new Row(150L, null, true, 2L, 150L, false, false, 0));

        assertIndices(rows, Map.of(150L, 0L));
    }

    @Test
    void hookChainSiblingsInheritHookIndex() {
        var rows = List.of(
                new Row(100L, null, false, 0L, 0L, false, false, 0), // plain CryptoTransfer parent, no contract result
                new Row(
                        101L,
                        100L,
                        true,
                        HOOK_CONTRACT_ID,
                        50L,
                        false,
                        false,
                        1), // hook execution, child of the transfer
                new Row(
                        102L, 100L, true, 999L, 30L, false, false,
                        1), // sibling, also child of the transfer, not of the hook
                new Row(200L, null, true, 3L, 200L, false, false, 0) // unrelated later root
                );

        assertIndices(rows, Map.of(101L, 0L, 102L, 0L, 200L, 1L));
    }

    @Test
    void hookChainBreaksOnNonContractSibling() {
        var rows = List.of(
                new Row(100L, null, false, 0L, 0L, false, false, 0), // plain CryptoTransfer parent
                new Row(101L, 100L, true, HOOK_CONTRACT_ID, 50L, false, false, 1), // hook execution
                new Row(
                        102L, 100L, false, 0L, 0L, false, false,
                        1), // unrelated sibling with no contract result - breaks chain
                new Row(
                        103L, 100L, true, 999L, 30L, false, false,
                        1) // sibling after the break - must claim its own index
                );

        assertIndices(rows, Map.of(101L, 0L, 103L, 1L));
    }

    @Test
    void hookItselfDoesNotLookUpItsOwnHookParent() {
        var rows = List.of(
                new Row(100L, null, false, 0L, 0L, false, false, 0),
                new Row(101L, 100L, true, HOOK_CONTRACT_ID, 50L, false, false, 1));

        assertIndices(rows, Map.of(101L, 0L));
    }

    @Test
    void hookNestedWithinContractExecutingParentInheritsFromParentNotHook() {
        var rows = List.of(
                new Row(100L, null, true, 1L, 500L, false, false, 0), // real top-level contract call
                new Row(
                        101L,
                        100L,
                        true,
                        HOOK_CONTRACT_ID,
                        50L,
                        false,
                        false,
                        1) // hook triggered during that call's execution
                );

        assertIndices(rows, Map.of(100L, 0L, 101L, 0L));
    }

    @Test
    void secondHookExecutionInSameChainClaimsFreshIndex() {
        var rows = List.of(
                new Row(100L, null, false, 0L, 0L, false, false, 0), // cryptoTransfer
                new Row(101L, 100L, true, HOOK_CONTRACT_ID, 50L, false, false, 1), // hookExecution1
                new Row(102L, 100L, true, 999L, 30L, false, false, 1), // nestedHookChild
                new Row(103L, 100L, true, HOOK_CONTRACT_ID, 50L, false, false, 1) // hookExecution2
                );

        assertIndices(rows, Map.of(101L, 0L, 102L, 0L, 103L, 1L));
    }

    @Test
    void twoRootsEachWithChildrenGetDistinctSharedIndices() {
        var rows = List.of(
                new Row(100L, null, true, 1L, 1000L, false, false, 0), // firstRoot
                new Row(101L, 100L, true, 2L, 2000L, false, false, 1), // firstChild
                new Row(102L, null, true, 3L, 3000L, false, false, 0), // secondRoot
                new Row(103L, 102L, true, 4L, 4000L, false, false, 1), // secondChild
                new Row(
                        104L, 102L, true, 5L, 5000L, false, false,
                        1) // secondChild2, parent is secondRoot not secondChild
                );

        assertIndices(rows, Map.of(100L, 0L, 101L, 0L, 102L, 1L, 103L, 1L, 104L, 1L));
    }

    @Test
    void precompileDispatchedChildrenOfDifferentTypesInheritRootIndex() {
        var rows = List.of(
                new Row(100L, null, true, 1L, 1000L, false, false, 0),
                new Row(101L, 100L, true, 1L, 500L, false, false, 1),
                new Row(102L, 100L, true, 1L, 600L, false, false, 1),
                new Row(103L, 100L, true, 1L, 700L, false, false, 1));

        assertIndices(rows, Map.of(100L, 0L, 101L, 0L, 102L, 0L, 103L, 0L));
    }

    @Test
    void atomicBatchInnerTransactionsGetSequentialIndices() {
        var rows = List.of(
                new Row(100L, null, false, 0L, 0L, false, false, 0), // ATOMIC_BATCH, no contract result
                new Row(200L, 100L, true, 1L, 500L, false, false, 0), // inner CONTRACTCALL
                new Row(300L, 100L, true, 1L, 600L, false, false, 0) // inner ETHEREUMTRANSACTION, same parent as above
                );

        assertIndices(rows, Map.of(200L, 0L, 300L, 1L));
    }

    @Test
    void atomicBatchInnerCallAfterHookChainClaimsItsOwnIndex() {
        var rows = List.of(
                new Row(100L, null, false, 0L, 0L, false, false, 0), // ATOMIC_BATCH
                new Row(101L, 100L, false, 0L, 0L, false, false, 0), // inner CryptoTransfer with hooks
                new Row(102L, 101L, true, HOOK_CONTRACT_ID, 50L, false, false, 1), // hook execution
                new Row(103L, 101L, true, 359L, 30L, false, false, 2), // call made by the hook
                new Row(104L, 100L, true, 1234L, 40L, false, false, 0)); // user-signed inner CONTRACTCALL

        assertIndices(rows, Map.of(102L, 0L, 103L, 0L, 104L, 1L));
        assertChildren(rows, 103L);
    }

    @Test
    void atomicBatchInnerCallAfterScheduledCallClaimsItsOwnIndex() {
        var rows = List.of(
                new Row(100L, null, false, 0L, 0L, false, false, 0), // ATOMIC_BATCH
                new Row(101L, 100L, false, 0L, 0L, false, false, 0), // inner ScheduleSign
                new Row(102L, null, true, 1L, 227547L, false, true, 0), // scheduled call
                new Row(103L, 101L, true, 359L, 15284L, false, false, 1), // effect of the scheduled call
                new Row(104L, 100L, true, 1234L, 40L, false, false, 0)); // user-signed inner CONTRACTCALL

        assertIndices(rows, Map.of(102L, 0L, 103L, 0L, 104L, 1L));
        assertChildren(rows, 103L);
    }

    @Test
    void multipleIndependentHookChainsInSameBlock() {
        var rows = List.of(
                new Row(100L, null, false, 0L, 0L, false, false, 0),
                new Row(101L, 100L, true, HOOK_CONTRACT_ID, 50L, false, false, 1),
                new Row(102L, 100L, true, 999L, 30L, false, false, 1),
                new Row(300L, null, false, 0L, 0L, false, false, 0),
                new Row(301L, 300L, true, HOOK_CONTRACT_ID, 50L, false, false, 1),
                new Row(302L, 300L, true, 888L, 30L, false, false, 1));

        assertIndices(rows, Map.of(101L, 0L, 102L, 0L, 301L, 1L, 302L, 1L));
    }

    @Test
    void findChildrenOfContractParents() {
        var rows = List.of(
                new Row(100L, null, true, 1L, 100L, false, false, 0), // root
                new Row(101L, 100L, true, 1L, 0L, false, false, 1), // child of a contract result
                new Row(200L, null, false, 0L, 0L, false, false, 0), // atomic batch, no contract result
                new Row(201L, 200L, true, 2L, 100L, false, false, 0), // inner call is its own EVM transaction
                new Row(202L, 201L, true, 359L, 100L, false, false, 1)); // effect of the inner call

        assertChildren(rows, 101L, 202L);
    }

    @Test
    void findChildrenOfHookChains() {
        var rows = List.of(
                new Row(100L, null, false, 0L, 0L, false, false, 0), // plain CryptoTransfer parent
                new Row(101L, 100L, true, HOOK_CONTRACT_ID, 50L, false, false, 1), // hook execution is a root
                new Row(102L, 100L, true, 999L, 30L, false, false, 1), // dispatched by the hook
                new Row(103L, 100L, false, 0L, 0L, false, false, 1), // breaks the chain
                new Row(104L, 100L, true, 999L, 30L, false, false, 1)); // after the break, a root again

        assertChildren(rows, 102L);
    }

    @Test
    void findChildrenOfScheduledCall() {
        var rows = List.of(
                new Row(100L, null, false, 0L, 0L, false, false, 0), // ScheduleSign
                new Row(101L, null, true, 1L, 227547L, false, true, 0), // scheduled call is a root
                new Row(102L, 100L, true, 359L, 15284L, false, false, 1), // effects are parented to the ScheduleSign
                new Row(103L, 100L, true, 359L, 2607L, false, false, 1));

        assertChildren(rows, 102L, 103L);
    }

    @Test
    void findChildrenIgnoresSyntheticLogOnlyRows() {
        var rows = List.of(
                new Row(100L, null, true, 1L, 100L, false, false, 0),
                new Row(101L, 100L, false, 0L, 0L, true, false, 1)); // synthetic log only, no contract result

        assertChildren(rows);
    }

    private static void assertChildren(List<Row> rows, Long... expected) {
        assertThat(EvmTransactionIndexCalculator.findChildren(rows, HOOK_CONTRACT_ID))
                .containsExactly(expected);
    }

    private static void assertIndices(List<Row> rows, Map<Long, Long> expected) {
        var results = EvmTransactionIndexCalculator.compute(rows, HOOK_CONTRACT_ID);
        var actual = new java.util.HashMap<Long, Long>();
        results.forEach(r -> actual.put(r.consensusTimestamp(), r.transactionIndex()));
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }
}
