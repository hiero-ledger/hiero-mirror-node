// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import lombok.Getter;

/**
 * Records the last 5 latency values in milliseconds, and calculates its average
 */
final class Latency {

    private static final int HISTORY_SIZE = 5;

    @Getter
    private long average = Long.MIN_VALUE;

    private int count = 0;
    private final long[] history = new long[HISTORY_SIZE];

    void record(long latency) {
        history[count++ % HISTORY_SIZE] = latency;
        if (count < 5) {
            return;
        }

        if (count >= 10) {
            // avoid overflow
            count = HISTORY_SIZE + (count % HISTORY_SIZE);
        }

        long sum = 0;
        int available = Math.min(HISTORY_SIZE, count);
        for (int i = 0; i < available; i++) {
            sum += history[i];
        }

        average = sum / available;
    }
}
