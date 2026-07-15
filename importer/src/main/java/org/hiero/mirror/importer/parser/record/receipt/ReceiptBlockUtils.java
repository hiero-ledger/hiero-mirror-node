// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.function.ToLongFunction;

/**
 * Helpers for partitioning contract results and logs into the blocks that own them.
 */
public final class ReceiptBlockUtils {

    private ReceiptBlockUtils() {}

    /**
     * Groups items into their enclosing blocks by consensus timestamp. An item belongs to the block whose consensus
     * range {@code [start, end]} contains its timestamp; items falling outside every block are dropped.
     *
     * @param items               the items to group
     * @param consensusEndByStart block consensus end keyed by consensus start, ordered by start
     * @param timestampExtractor  extracts an item's consensus timestamp
     * @return items grouped by their block's consensus start
     */
    public static <T> Map<Long, List<T>> groupByBlock(
            Collection<T> items, NavigableMap<Long, Long> consensusEndByStart, ToLongFunction<T> timestampExtractor) {
        final var grouped = new HashMap<Long, List<T>>();
        for (final var item : items) {
            final var timestamp = timestampExtractor.applyAsLong(item);
            final var block = consensusEndByStart.floorEntry(timestamp);
            if (block != null && timestamp <= block.getValue()) {
                grouped.computeIfAbsent(block.getKey(), k -> new ArrayList<>()).add(item);
            }
        }

        return grouped;
    }
}
