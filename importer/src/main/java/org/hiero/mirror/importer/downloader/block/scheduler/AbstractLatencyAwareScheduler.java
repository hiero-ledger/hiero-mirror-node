// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.SchedulerProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;

@RequiredArgsConstructor
abstract class AbstractLatencyAwareScheduler extends AbstractScheduler {

    protected final LatencyService latencyService;
    protected final SchedulerProperties schedulerProperties;

    protected final List<BlockNode> candidates = new CopyOnWriteArrayList<>();
    protected final AtomicReference<BlockNode> current = new AtomicReference<>();
    protected final AtomicLong lastScheduledTime = new AtomicLong(0);

    protected long lastPostProcessingLatency;

    @Override
    public BlockNode getNode(long blockNumber) {
        try {
            latencyService.cancelAll();
            current.set(super.getNode(blockNumber));
            candidates.clear();
            candidates.addAll(getCandidates());
            latencyService.setNodes(candidates);
            lastScheduledTime.set(System.currentTimeMillis());
            return current.get();
        } catch (BlockStreamException ex) {
            current.set(null);
            throw ex;
        }
    }

    @Override
    public boolean shouldReschedule(BlockFile blockFile, BlockStream blockStream) {
        long previousPostProcessingLatency = lastPostProcessingLatency;
        lastPostProcessingLatency = System.currentTimeMillis() - blockStream.blockCompleteTime();

        // when post-processing takes too long, it can significantly delay block stream response processing and skew the
        // latency. Therefore, latency should only be measured and recorded under low post-processing latency conditions
        if (previousPostProcessingLatency
                > schedulerProperties.getMaxPostProcessingLatency().toMillis()) {
            return false;
        }

        long latency = Utils.getLatency(blockFile, blockStream);
        current.get().recordLatency(latency);

        if (System.currentTimeMillis() - lastScheduledTime.get()
                < schedulerProperties.getMinRescheduleInterval().toMillis()) {
            return false;
        }

        long updatedLatency = current.get().getLatency();
        for (var candidate : candidates) {
            if (updatedLatency
                    >= candidate.getLatency()
                            + schedulerProperties
                                    .getRescheduleLatencyThreshold()
                                    .toMillis()) {
                return true;
            }
        }

        return false;
    }

    protected abstract Iterator<BlockNode> getNodeGroupIterator();

    private Collection<BlockNode> getCandidates() {
        var iter = getNodeGroupIterator();
        while (iter.hasNext()) {
            var node = iter.next();
            if (node == current.get()) {
                break;
            }
        }

        return Lists.newArrayList(iter);
    }
}
