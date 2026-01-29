// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import jakarta.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.downloader.block.BlockStreamVerifier;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * A latency service to measure a selection of block nodes' streaming latency in background. The latency measuring tasks
 * are scheduled in fixed delay, and limited to up to 1 + backlog at a time.
 */
@CustomLog
@Named
@RequiredArgsConstructor
public final class LatencyService implements AutoCloseable {

    private final BlockProperties blockProperties;
    private final BlockStreamReader blockStreamReader;
    private final BlockStreamVerifier blockStreamVerifier;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final ThreadPoolExecutor executor = createExecutor();

    private final AtomicLong generation = new AtomicLong();
    private final List<Future<?>> results = new CopyOnWriteArrayList<>();
    private final List<Task> tasks = new CopyOnWriteArrayList<>();

    @Override
    public void close() {
        cancelAll();
        if (generation.get() > 0) {
            getExecutor().shutdown();
        }
    }

    void cancelAll() {
        results.forEach(r -> r.cancel(true));
        results.clear();
        tasks.clear();
    }

    /**
     * Set the block nodes to asynchronously measure latency for
     *
     * @param nodes - Block nodes
     */
    void setNodes(Collection<BlockNode> nodes) {
        cancelAll();

        long bornGeneration = generation.incrementAndGet();
        nodes.forEach(blockNode -> tasks.add(new Task(bornGeneration, blockNode)));
    }

    @Scheduled(fixedDelayString = "#{@blockProperties.getScheduler().getLatencyService().getFrequency().toMillis()}")
    public void schedule() {
        // drain completed futures
        results.removeIf(Future::isDone);

        if (tasks.isEmpty()) {
            return;
        }

        final var executor = getExecutor();
        final int backlog = blockProperties.getScheduler().getLatencyService().getBacklog();
        for (int i = 0; i < backlog + 1; i++) {
            Task task = null;
            try {
                task = tasks.removeFirst();
                results.add(executor.submit(task));
            } catch (NoSuchElementException e) {
                break;
            } catch (RejectedExecutionException e) {
                tasks.addFirst(task);
                break;
            }
        }
    }

    private ThreadPoolExecutor createExecutor() {
        // a single-thread threadpool executor with a blocking queue to holding the backlog, to schedule 1 running
        // + backlog pending scheduled tasks
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(
                        blockProperties.getScheduler().getLatencyService().getBacklog()));
    }

    @RequiredArgsConstructor
    private final class Task implements Runnable {

        private final long bornGeneration;
        private final BlockNode node;

        @Override
        public void run() {
            if (bornGeneration != generation.get()) {
                // return without adding the task back to the tasks list if it's already a different generation
                return;
            }

            final long nextBlockNumber = blockStreamVerifier.getNextBlockNumber();
            if (nextBlockNumber < 0 || !node.getBlockRange().contains(nextBlockNumber)) {
                return;
            }

            log.info("Measuring {}'s latency by streaming block {}", node, nextBlockNumber);
            final var timeout =
                    blockProperties.getScheduler().getLatencyService().getTimeout();
            node.streamBlocks(nextBlockNumber, nextBlockNumber, this::measureLatency, timeout);

            if (bornGeneration == generation.get()) {
                // add the task back to the list if it's still the current generation, so it'll get rescheduled
                tasks.add(this);
            }
        }

        private boolean measureLatency(final BlockStream blockStream, final String blockNode) {
            final var blockFile = blockStreamReader.read(blockStream);
            node.recordLatency(Utils.getLatency(blockFile, blockStream));
            return false;
        }
    }
}
