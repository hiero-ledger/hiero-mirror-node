// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * Records the latency in milliseconds and calculates the exponential moving average using a factor of 0.3
 */
public final class Latency {

    private static final long NO_MAX_AGE = Long.MAX_VALUE;

    // A smoothing factor of 0.3 gives faster reaction and is also noisier
    private static final double SMOOTHING_FACTOR = 0.3;

    private volatile double average;
    private volatile boolean initialized;

    @Getter(value = AccessLevel.PACKAGE)
    private volatile long lastMeasuredMillis;

    private final AtomicBoolean stale = new AtomicBoolean(false);

    double getAverage() {
        return getAverage(NO_MAX_AGE);
    }

    /**
     * Gets the average, treating a latency last measured longer than maxAgeMillis ago as unmeasured.
     */
    double getAverage(final long maxAgeMillis) {
        return isFresh(maxAgeMillis) ? average : Double.MAX_VALUE;
    }

    double getMetricValue() {
        final var average = getAverage(NO_MAX_AGE);
        return average != Double.MAX_VALUE ? average : Double.NaN;
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

    private boolean isFresh(final long maxAgeMillis) {
        return initialized
                && !stale.get()
                && (maxAgeMillis == NO_MAX_AGE || System.currentTimeMillis() - lastMeasuredMillis <= maxAgeMillis);
    }
}
