// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records the latency in milliseconds and calculates the exponential moving average using a factor of 0.3
 */
public final class Latency implements Comparable<Latency> {

    private static final Comparator<Latency> COMPARATOR = Comparator.comparing(Latency::getAverage);

    // A smoothing factor of 0.3 gives faster reaction and is also noisier
    private static final double SMOOTHING_FACTOR = 0.3;

    private double average;
    private boolean initialized;
    private final AtomicBoolean stale = new AtomicBoolean(false);

    @Override
    public int compareTo(final Latency other) {
        return COMPARATOR.compare(this, other);
    }

    double getAverage() {
        return stale.get() ? Double.MAX_VALUE : average;
    }

    void markStale() {
        stale.set(true);
    }

    synchronized void record(final long latency) {
        stale.set(false);
        if (!initialized) {
            average = latency;
            initialized = true;
        } else {
            average = SMOOTHING_FACTOR * latency + (1.0 - SMOOTHING_FACTOR) * average;
        }
    }
}
