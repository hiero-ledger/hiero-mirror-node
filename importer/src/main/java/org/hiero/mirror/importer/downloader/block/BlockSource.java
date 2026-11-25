// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

interface BlockSource {

    String FAILOVER_METRIC_NAME = "hiero.mirror.importer.block.failover";
    String FAILOVER_TYPE_TAG = "type";

    /**
     * Registers the failover counter of the given failover type, or returns it if it's already registered.
     */
    static Counter failoverMetric(final MeterRegistry meterRegistry, final FailoverType type) {
        return Counter.builder(FAILOVER_METRIC_NAME)
                .description("The number of times the importer failed over from block nodes to cloud storage, or "
                        + "from the canonical block file path to a consensus node block file path")
                .tag(FAILOVER_TYPE_TAG, type.name())
                .register(meterRegistry);
    }

    /*
     * Gets blocks from the source.
     */
    void get();

    enum FailoverType {
        CLOUD_STORAGE, // From block nodes to cloud storage
        CONSENSUS_NODE // From the canonical block file path to a consensus node block file path
    }
}
