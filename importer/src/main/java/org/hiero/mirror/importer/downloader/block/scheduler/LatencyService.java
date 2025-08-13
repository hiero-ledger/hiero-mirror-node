// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import jakarta.inject.Named;
import java.time.Duration;
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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockStreamVerifier;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * A latency service measures a selection of block nodes' streaming latency in background. The latency measuring tasks
 * are scheduled in fixed delay, and limited to up to 2 at a time.
 */
@Named
@RequiredArgsConstructor
public class LatencyService implements AutoCloseable {

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

    void setNodes(Collection<BlockNode> nodes) {
        cancelAll();

        long bornGeneration = generation.incrementAndGet();
        nodes.forEach(blockNode -> tasks.add(new Task(bornGeneration, blockNode)));
    }

    @Scheduled(fixedDelay = 5000)
    void scheduleTasks() {
        // drain completed futures
        results.removeIf(Future::isDone);

        if (tasks.isEmpty()) {
            return;
        }

        var executor = getExecutor();
        for (int i = 0; i < 2; i++) {
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
        // a single thread threadpool executor with a blocking queue with capacity 1, to limit to 1 running task and 1
        // pending task
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
    }

    @RequiredArgsConstructor
    private class Task implements Runnable {

        private final long bornGeneration;
        private final BlockNode node;

        @Override
        public void run() {
            if (bornGeneration != generation.get()) {
                // return without adding the task back to the tasks list if it's already a different generation
                return;
            }

            long nextBlockNumber = blockStreamVerifier.getNextBlockNumber();
            if (!node.hasBlock(nextBlockNumber)) {
                return;
            }

            node.streamBlocks(nextBlockNumber, nextBlockNumber, this::measureLatency, Duration.ofSeconds(5));

            if (bornGeneration == generation.get()) {
                tasks.add(this);
            }
        }

        private boolean measureLatency(BlockStream blockStream) {
            var blockFile = blockStreamReader.read(blockStream);
            node.recordLatency(Utils.getLatency(blockFile, blockStream));
            return false;
        }
    }
}
