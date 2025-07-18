// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import lombok.Getter;

/**
 * Records the last 5 latency values in milliseconds, and calculates its average
 */
final class Latency {

    @Getter
    private long average;

    private int count = 0;
    private final long[] history = new long[5];

    void record(long latency) {
        history[count++ % 5] = latency;
        if (count >= 10) {
            // avoid overflow
            count -= 5;
        }

        long sum = 0;
        int available = Math.min(5, count);
        for (int i = 0; i < available; i++) {
            sum += history[i];
        }

        average = sum / available;
    }
}
