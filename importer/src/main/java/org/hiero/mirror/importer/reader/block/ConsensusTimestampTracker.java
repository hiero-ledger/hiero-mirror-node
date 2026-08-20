// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import lombok.Getter;
import org.hiero.mirror.importer.util.Utility;

/** Tracks min/max consensus timestamps while reading stream items. */
@Getter
public final class ConsensusTimestampTracker {

    private long minTimestamp = Long.MAX_VALUE;
    private long maxTimestamp = Long.MIN_VALUE;

    public void track(final long timestamp) {
        if (timestamp < minTimestamp) {
            minTimestamp = timestamp;
        }
        if (timestamp > maxTimestamp) {
            maxTimestamp = timestamp;
        }
    }

    public Bounds validateItemOrder(
            final String fileName, final long firstItemTimestamp, final long lastItemTimestamp) {
        if (minTimestamp != firstItemTimestamp) {
            reportMinOutOfOrder(fileName, minTimestamp, firstItemTimestamp);
        }
        if (maxTimestamp != lastItemTimestamp) {
            reportMaxOutOfOrder(fileName, maxTimestamp, lastItemTimestamp);
        }
        return new Bounds(minTimestamp, maxTimestamp);
    }

    private void reportMinOutOfOrder(final String fileName, final long minTimestamp, final long firstTimestamp) {
        Utility.handleRecoverableError(
                "File {} has out-of-order transactions: min consensus timestamp {} != first transaction timestamp {}",
                fileName,
                minTimestamp,
                firstTimestamp);
    }

    private void reportMaxOutOfOrder(String fileName, long maxTimestamp, long lastTimestamp) {
        Utility.handleRecoverableError(
                "File {} has out-of-order transactions: max consensus timestamp {} != last transaction timestamp {}",
                fileName,
                maxTimestamp,
                lastTimestamp);
    }

    public record Bounds(long start, long end) {}
}
