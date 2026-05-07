// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import lombok.Getter;

/**
 * Records the latency in milliseconds and calculates the exponential moving average using a factor of 0.3
 */
final class Latency {

    // A smoothing factor of 0.3 gives faster reaction and is also noisier
    private static final double SMOOTHING_FACTOR = 0.3;

    @Getter
    private double average;

    private boolean initialized;

    void record(final long latency) {
        if (!initialized) {
            average = latency;
            initialized = true;
        } else {
            average = SMOOTHING_FACTOR * latency + (1.0 - SMOOTHING_FACTOR) * average;
        }
    }
}
