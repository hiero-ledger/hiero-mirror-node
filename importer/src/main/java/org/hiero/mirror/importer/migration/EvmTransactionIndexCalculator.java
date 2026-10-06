// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

final class EvmTransactionIndexCalculator {

    private EvmTransactionIndexCalculator() {}

    static List<Result> compute(@NonNull List<Row> rows, long hookContractId) {
        var results = new ArrayList<Result>();
        var indexByTimestamp = new HashMap<Long, Long>();
        var byTimestamp = indexRowsByTimestamp(rows);
        long counter = 0L;

        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            if (!row.hasContractResult() && !row.syntheticLogOnly()) {
                continue;
            }

            var contractRelatedParentTimestamp = resolveContractRelatedParent(rows, byTimestamp, i, hookContractId);

            Long evmIndex;
            if (contractRelatedParentTimestamp != null
                    && indexByTimestamp.containsKey(contractRelatedParentTimestamp)) {
                evmIndex = indexByTimestamp.get(contractRelatedParentTimestamp);
            } else if (row.syntheticLogOnly() || row.gasUsed() > 0) {
                evmIndex = counter++;
            } else {
                evmIndex = null;
            }

            if (evmIndex != null) {
                indexByTimestamp.put(row.consensusTimestamp(), evmIndex);
            }
            results.add(new Result(row.consensusTimestamp(), evmIndex));
        }

        return results;
    }

    private static @Nullable Long resolveContractRelatedParent(
            List<Row> rows, Map<Long, Integer> byTimestamp, int currentIndex, long hookContractId) {
        var current = rows.get(currentIndex);

        if (current.hasContractResult()
                && current.parentConsensusTimestamp() != null
                && current.contractId() != hookContractId) {
            for (int j = currentIndex - 1; j >= 0; j--) {
                var candidate = rows.get(j);
                if (candidate.consensusTimestamp() == current.parentConsensusTimestamp()) {
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

        if (current.parentConsensusTimestamp() != null) {
            var parentIndex = byTimestamp.get(current.parentConsensusTimestamp());
            if (parentIndex != null) {
                var parent = rows.get(parentIndex);
                if (parent.hasContractResult()) {
                    return parent.consensusTimestamp();
                }
            }
        }

        return null;
    }

    private static Map<Long, Integer> indexRowsByTimestamp(List<Row> rows) {
        var map = new HashMap<Long, Integer>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            map.put(rows.get(i).consensusTimestamp(), i);
        }
        return map;
    }

    record Row(
            long consensusTimestamp,
            @Nullable Long parentConsensusTimestamp,
            boolean hasContractResult,
            long contractId,
            long gasUsed,
            boolean syntheticLogOnly) {}

    record Result(long consensusTimestamp, @Nullable Long evmIndex) {}
}
