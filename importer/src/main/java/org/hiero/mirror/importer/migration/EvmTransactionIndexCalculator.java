// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
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

            if (evmIndex != null && row.hasContractResult()) {
                indexByTimestamp.put(row.consensusTimestamp(), evmIndex);
            }

            if (row.differsFrom(evmIndex)) {
                updates.add(new Update(row.consensusTimestamp(), evmIndex));
            }
        }

        return updates;
    }

    private static @Nullable Long resolveContractRelatedParent(List<Row> rows, int currentIndex, long hookContractId) {
        final var current = rows.get(currentIndex);
        final var parentTimestamp = current.parentConsensusTimestamp();
        if (parentTimestamp == null) {
            return null;
        }

        if (current.hasContractResult() && current.contractId() != hookContractId) {
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
                if (candidate.contractId() == hookContractId) {
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
            @Nullable Long contractResultIndex,
            long logCount,
            long nullLogIndexCount,
            @Nullable Long minLogIndex,
            @Nullable Long maxLogIndex) {

        boolean differsFrom(@Nullable Long evmIndex) {
            final var resultDiffers = hasContractResult && !Objects.equals(contractResultIndex, evmIndex);
            return resultDiffers || logsDifferFrom(evmIndex);
        }

        private boolean logsDifferFrom(@Nullable Long evmIndex) {
            if (logCount == 0) {
                return false;
            }

            if (evmIndex == null) {
                return nullLogIndexCount != logCount;
            }

            return nullLogIndexCount != 0 || !evmIndex.equals(minLogIndex) || !evmIndex.equals(maxLogIndex);
        }
    }

    record Update(long consensusTimestamp, @Nullable Long transactionIndex) {}
}
