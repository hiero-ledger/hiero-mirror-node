// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;

@RequiredArgsConstructor
abstract class AbstractLatencyAwareScheduler extends AbstractScheduler {

    protected final BlockProperties blockProperties;
    protected final LatencyService latencyService;

    protected long lastPostProcessingLatency;
    protected AtomicReference<BlockNode> current;

    private Collection<BlockNode> candidates = Collections.emptyList();

    @Override
    public BlockNode getNode(long blockNumber) {
        try {
            latencyService.cancelAll();
            current.set(super.getNode(blockNumber));
            candidates = getCandidates();
            latencyService.setNodes(candidates);
            return current.get();
        } catch (BlockStreamException ex) {
            current.set(null);
            throw ex;
        }
    }

    @Override
    public boolean shouldRescheduleOnBlockProcessed(BlockFile blockFile, BlockStream blockStream) {
        long previousPostProcessingLatency = lastPostProcessingLatency;
        lastPostProcessingLatency = System.currentTimeMillis() - blockStream.blockCompleteTime();

        var scheduling = blockProperties.getScheduling();
        // when post-processing takes too long, it can significantly delay block stream response processing and skew the
        // latency. Therefore, latency should only be measured and recorded under low post-processing latency conditions
        if (previousPostProcessingLatency
                > scheduling.getMaxPostProcessingLatency().toMillis()) {
            return false;
        }

        long latency = Utils.getLatency(blockFile, blockStream);
        current.get().recordLatency(latency);
        long updatedLatency = current.get().getLatency();
        for (var candidate : candidates) {
            if (updatedLatency - candidate.getLatency()
                    >= scheduling.getMaxPostProcessingLatency().toMillis()) {
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
