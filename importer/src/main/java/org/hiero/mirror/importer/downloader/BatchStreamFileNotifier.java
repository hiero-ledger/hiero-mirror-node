// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import jakarta.inject.Named;
import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.AbstractParserProperties.BatchProperties;
import org.hiero.mirror.importer.parser.StreamFileParser;
import org.hiero.mirror.importer.parser.balance.AccountBalanceFileParser;
import org.hiero.mirror.importer.parser.record.RecordFileParser;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@CustomLog
@NullMarked
@Named
@Order(Ordered.HIGHEST_PRECEDENCE)
final class BatchStreamFileNotifier implements StreamFileNotifier, Closeable {

    private final StreamFileSubscriber balanceStreamFileSubscriber;
    private final StreamFileSubscriber recordStreamFileSubscriber;
    private final ExecutorService executorService;

    BatchStreamFileNotifier(AccountBalanceFileParser accountBalanceFileParser, RecordFileParser recordFileParser) {
        balanceStreamFileSubscriber = new StreamFileSubscriber(accountBalanceFileParser);
        recordStreamFileSubscriber = new StreamFileSubscriber(recordFileParser);
        executorService = Executors.newFixedThreadPool(2);
        executorService.execute(balanceStreamFileSubscriber);
        executorService.execute(recordStreamFileSubscriber);
    }

    @Override
    public void close() {
        executorService.close();
    }

    @Override
    public void verified(StreamFile<?> streamFile) {
        var streamFileSubscriber =
                switch (streamFile.getType()) {
                    case BALANCE -> balanceStreamFileSubscriber;
                    case RECORD -> recordStreamFileSubscriber;
                    default ->
                        throw new IllegalArgumentException("Unsupported stream file type: " + streamFile.getType());
                };
        streamFileSubscriber.notify(streamFile);
        log.debug("Published {}", streamFile);
    }

    private class StreamFileSubscriber implements Runnable {

        private final Collection<StreamFile<?>> buffer;
        private final AtomicLong files;
        private final AtomicLong items;
        private final AtomicReference<Instant> lastFlush;
        private final BatchProperties properties;
        private final BlockingQueue<StreamFile<?>> queue;
        private final StreamFileParser<StreamFile<?>> streamFileParser;

        @SuppressWarnings("unchecked")
        StreamFileSubscriber(StreamFileParser<? extends StreamFile<?>> streamFileParser) {
            this.buffer = new ArrayList<>(); // Un-synchronized since only one thread reads and writes from it
            this.files = new AtomicLong(0L);
            this.items = new AtomicLong(0L);
            this.lastFlush = new AtomicReference<>(Instant.now());
            this.properties = streamFileParser.getProperties().getBatch();
            this.queue = new ArrayBlockingQueue<>(properties.getQueueCapacity());
            this.streamFileParser = (StreamFileParser<StreamFile<?>>) streamFileParser;
        }

        @SneakyThrows
        public void notify(StreamFile<?> streamFile) {
            queue.put(streamFile);
        }

        @Override
        public void run() {
            long frequency = streamFileParser.getProperties().getFrequency().toMillis();

            while (!executorService.isShutdown()) {
                try {
                    var streamFile = queue.poll(frequency, TimeUnit.MILLISECONDS);
                    handle(streamFile);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.error("Error during parsing", e);
                }
            }
        }

        private void handle(StreamFile<?> streamFile) {
            if (streamFile == null) {
                // Handle the corner case where we don't receive a new file for some time to trigger a flush
                if (!buffer.isEmpty() && exceedsInterval()) {
                    streamFileParser.parse(new ArrayList<>(buffer));
                    reset();
                }
                return;
            }

            if (!flush(streamFile)) {
                buffer.add(streamFile);
                return;
            }

            // Flush the buffer, optimizing for the single item scenario
            if (buffer.isEmpty()) {
                streamFileParser.parse(streamFile);
            } else {
                buffer.add(streamFile);
                streamFileParser.parse(new ArrayList<>(buffer));
            }

            reset();
        }

        /**
         * Determines whether the given stream file should trigger a flush of its buffer. The stream file triggering the
         * flush will be included within the batch.
         *
         * @param streamFile to check
         * @return true when the buffer should be flushed
         */
        private boolean flush(StreamFile<?> streamFile) {
            long count = items.addAndGet(streamFile.getCount());

            // Flush the buffer when the file count exceeds the maximum expected number of files
            if (files.incrementAndGet() >= properties.getMaxFiles()) {
                return true;
            }

            // Flush the buffer when the item count exceeds the maximum expected number of items
            if (count >= properties.getMaxItems()) {
                return true;
            }

            // Flush the buffer if stream file processing has caught up
            long lag = DomainUtils.now() - streamFile.getConsensusEnd();
            if (lag <= properties.getWindow().toNanos()) {
                return true;
            }

            return exceedsInterval();
        }

        // Flush the buffer if enough time has elapsed from the last flush
        private boolean exceedsInterval() {
            var elapsed = Duration.between(lastFlush.get(), Instant.now());
            return elapsed.compareTo(properties.getFlushInterval()) >= 0;
        }

        private void reset() {
            buffer.clear();
            files.set(0L);
            items.set(0L);
            lastFlush.set(Instant.now());
        }
    }
}
