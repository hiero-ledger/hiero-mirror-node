// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

final class EvmTransactionIndexCalculator {

    private EvmTransactionIndexCalculator() {}

    static List<Update> compute(@NonNull List<Row> rows, long hookContractId) {
        final var updates = new ArrayList<Update>();
        final var indexByTimestamp = new HashMap<Long, Long>();
        long counter = 0L;

        for (int i = 0; i < rows.size(); i++) {
            final var row = rows.get(i);
            if (!row.hasContractResult() && !row.syntheticLogOnly()) {
                continue;
            }

            final var contractRelatedParentTimestamp = resolveContractRelatedParent(rows, i, hookContractId);

            Long evmIndex;
            if (contractRelatedParentTimestamp != null
                    && indexByTimestamp.containsKey(contractRelatedParentTimestamp)) {
                evmIndex = indexByTimestamp.get(contractRelatedParentTimestamp);
            } else if (row.syntheticLogOnly() || row.gasUsed() > 0) {
                evmIndex = counter++;
            } else {
                evmIndex = null;
            }

            // A null index is kept too, so the effects of a call that claimed none don't claim one either
            if (row.hasContractResult()) {
                indexByTimestamp.put(row.consensusTimestamp(), evmIndex);
            }

            updates.add(new Update(row.consensusTimestamp(), evmIndex));
        }

        return updates;
    }

    // Timestamps of the contract results that inherit the index of a contract related parent in compute()
    static List<Long> findChildren(@NonNull List<Row> rows, long hookContractId) {
        final var childTimestamps = new ArrayList<Long>();
        final var contractResultTimestamps = new HashSet<Long>();

        for (int i = 0; i < rows.size(); i++) {
            final var row = rows.get(i);
            if (!row.hasContractResult()) {
                continue;
            }

            final var contractRelatedParentTimestamp = resolveContractRelatedParent(rows, i, hookContractId);
            if (contractRelatedParentTimestamp != null
                    && contractResultTimestamps.contains(contractRelatedParentTimestamp)) {
                childTimestamps.add(row.consensusTimestamp());
            }

            contractResultTimestamps.add(row.consensusTimestamp());
        }

        return childTimestamps;
    }

    private static @Nullable Long resolveContractRelatedParent(List<Row> rows, int currentIndex, long hookContractId) {
        final var current = rows.get(currentIndex);
        final var parentTimestamp = current.parentConsensusTimestamp();
        if (parentTimestamp == null) {
            return null;
        }

        // Only child records (nonce > 0) can be hook or scheduled call effects, unlike atomic batch inner transactions
        if (current.nonce() > 0 && current.hasContractResult() && current.contractId() != hookContractId) {
            for (int j = currentIndex - 1; j >= 0; j--) {
                final var candidate = rows.get(j);
                if (candidate.consensusTimestamp() == parentTimestamp) {
                    // walked back onto the current row's own direct parent - stop, no hook found
                    break;
                }
                if (!candidate.hasContractResult()) {
                    // unbroken chain requirement - a gap means no hook parent
                    break;
                }
                // Effects of a scheduled call are parented to its trigger, so the call is found the same way as a hook
                if (candidate.contractId() == hookContractId || candidate.scheduled()) {
                    return candidate.consensusTimestamp();
                }
            }
        }

        return parentTimestamp;
    }

    record Row(
            long consensusTimestamp,
            @Nullable Long parentConsensusTimestamp,
            boolean hasContractResult,
            long contractId,
            long gasUsed,
            boolean syntheticLogOnly,
            boolean scheduled,
            int nonce) {}

    record Update(long consensusTimestamp, @Nullable Long transactionIndex) {}
}
