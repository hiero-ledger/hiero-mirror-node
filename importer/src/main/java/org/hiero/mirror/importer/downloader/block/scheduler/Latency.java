// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records the latency in milliseconds and calculates the exponential moving average using a factor of 0.3
 */
public final class Latency {

    // A smoothing factor of 0.3 gives faster reaction and is also noisier
    private static final double SMOOTHING_FACTOR = 0.3;

    private volatile double average;
    private volatile boolean initialized;
    private volatile long lastMeasuredMillis;
    private final AtomicBoolean stale = new AtomicBoolean(false);

    double getAverage() {
        return initialized && !stale.get() ? average : Double.MAX_VALUE;
    }

    /**
     * Gets the average, treating a latency last measured longer than maxAgeMillis ago as unmeasured.
     */
    double getAverage(final long maxAgeMillis) {
        return isFresh(maxAgeMillis) ? average : Double.MAX_VALUE;
    }

    long getLastMeasuredMillis() {
        return lastMeasuredMillis;
    }

    double getMetricValue() {
        return initialized && !stale.get() ? average : Double.NaN;
    }

    boolean isFresh(final long maxAgeMillis) {
        return initialized && !stale.get() && System.currentTimeMillis() - lastMeasuredMillis <= maxAgeMillis;
    }

    /**
     * Marks the latency stale.
     *
     * @return true if the latency wasn't already stale
     */
    boolean markStale() {
        return !stale.getAndSet(true);
    }

    synchronized void record(final long latency) {
        stale.set(false);
        lastMeasuredMillis = System.currentTimeMillis();
        if (!initialized) {
            average = latency;
            initialized = true;
        } else {
            average = SMOOTHING_FACTOR * latency + (1.0 - SMOOTHING_FACTOR) * average;
        }
    }
}
