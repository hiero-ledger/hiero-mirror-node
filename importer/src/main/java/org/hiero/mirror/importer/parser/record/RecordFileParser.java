// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.hiero.mirror.importer.config.DateRangeCalculator.DateRangeFilter;
import static org.hiero.mirror.importer.reader.record.ProtoRecordFileReader.VERSION;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.hiero.mirror.common.aggregator.LogsBloomAggregator;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.config.DateRangeCalculator;
import org.hiero.mirror.importer.parser.AbstractStreamFileParser;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.StreamFileRepository;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

@Named
public class RecordFileParser extends AbstractStreamFileParser<RecordFile> {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final RecordItemListener recordItemListener;
    private final DateRangeCalculator dateRangeCalculator;
    private final ParserContext parserContext;

    // Metrics
    private final Map<Integer, Timer> latencyMetrics;
    private final Map<Integer, DistributionSummary> sizeMetrics;
    private final Timer unknownLatencyMetric;
    private final DistributionSummary unknownSizeMetric;

    @SuppressWarnings("java:S107")
    public RecordFileParser(
            ApplicationEventPublisher applicationEventPublisher,
            MeterRegistry meterRegistry,
            RecordParserProperties parserProperties,
            StreamFileRepository<RecordFile, Long> streamFileRepository,
            RecordItemListener recordItemListener,
            RecordStreamFileListener recordStreamFileListener,
            DateRangeCalculator dateRangeCalculator,
            ParserContext parserContext) {
        super(meterRegistry, parserProperties, recordStreamFileListener, streamFileRepository);
        this.applicationEventPublisher = applicationEventPublisher;
        this.recordItemListener = recordItemListener;
        this.dateRangeCalculator = dateRangeCalculator;
        this.parserContext = parserContext;

        // build transaction latency metrics
        ImmutableMap.Builder<Integer, Timer> latencyMetricsBuilder = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, DistributionSummary> sizeMetricsBuilder = ImmutableMap.builder();

        for (TransactionType type : TransactionType.values()) {
            Timer timer = Timer.builder("hiero.mirror.importer.transaction.latency")
                    .description("The difference in ms between the time consensus was achieved and the mirror node "
                            + "processed the transaction")
                    .tag("type", type.toString())
                    .register(meterRegistry);
            latencyMetricsBuilder.put(type.getProtoId(), timer);

            DistributionSummary distributionSummary = DistributionSummary.builder(
                            "hiero.mirror.importer.transaction.size")
                    .description("The size of the transaction in bytes")
                    .baseUnit("bytes")
                    .tag("type", type.toString())
                    .register(meterRegistry);
            sizeMetricsBuilder.put(type.getProtoId(), distributionSummary);
        }

        latencyMetrics = latencyMetricsBuilder.build();
        sizeMetrics = sizeMetricsBuilder.build();
        unknownLatencyMetric = latencyMetrics.get(TransactionType.UNKNOWN.getProtoId());
        unknownSizeMetric = sizeMetrics.get(TransactionType.UNKNOWN.getProtoId());
    }

    /**
     * Given a stream file data representing an rcd file from the service parse record items and persist changes
     *
     * @param recordFile containing information about file to be processed
     */
    @Override
    @Retryable(
            backoff =
                    @Backoff(
                            delayExpression = "#{@recordParserProperties.getRetry().getMinBackoff().toMillis()}",
                            maxDelayExpression = "#{@recordParserProperties.getRetry().getMaxBackoff().toMillis()}",
                            multiplierExpression = "#{@recordParserProperties.getRetry().getMultiplier()}"),
            retryFor = Throwable.class,
            noRetryFor = OutOfMemoryError.class,
            maxAttemptsExpression = "#{@recordParserProperties.getRetry().getMaxAttempts()}")
    @Transactional(timeoutString = "#{@recordParserProperties.getTransactionTimeout().toSeconds()}")
    public synchronized void parse(RecordFile recordFile) {
        try {
            super.parse(recordFile);
        } finally {
            parserContext.clear();
        }
    }

    @Override
    @Retryable(
            backoff =
                    @Backoff(
                            delayExpression = "#{@recordParserProperties.getRetry().getMinBackoff().toMillis()}",
                            maxDelayExpression = "#{@recordParserProperties.getRetry().getMaxBackoff().toMillis()}",
                            multiplierExpression = "#{@recordParserProperties.getRetry().getMultiplier()}"),
            retryFor = Throwable.class,
            noRetryFor = OutOfMemoryError.class,
            maxAttemptsExpression = "#{@recordParserProperties.getRetry().getMaxAttempts()}")
    @Transactional(timeoutString = "#{@recordParserProperties.getTransactionTimeout().toSeconds()}")
    public synchronized void parse(List<RecordFile> recordFiles) {
        try {
            super.parse(recordFiles);
        } finally {
            parserContext.clear();
        }
    }

    @Override
    protected void doFlush(RecordFile streamFile) {
        super.doFlush(streamFile);
        applicationEventPublisher.publishEvent(new RecordFileParsedEvent(this, streamFile.getConsensusEnd()));
    }

    @Override
    protected void doParse(RecordFile recordFile) {
        DateRangeFilter dateRangeFilter = dateRangeCalculator.getFilter(parserProperties.getStreamType());
        var aggregator = new RecordItemAggregator();
        var count = new AtomicLong(0L);
        boolean shouldLog = log.isDebugEnabled() || log.isTraceEnabled();
        final var logIndex = new AtomicInteger(0);
        recordFile.getItems().forEach(recordItem -> {
            if (shouldLog) {
                logItem(recordItem);
            }

            aggregator.accept(recordItem);

            if (dateRangeFilter.filter(recordItem.getConsensusTimestamp())) {
                recordItem.setLogIndex(logIndex);
                recordItemListener.onItem(recordItem);
                recordMetrics(recordItem);
                count.incrementAndGet();
            }
        });

        recordFile.setCount(count.get());
        aggregator.update(recordFile);
        updateIndex(recordFile);

        parserContext.add(recordFile);
        parserContext.addAll(recordFile.getSidecars());
    }

    private void logItem(RecordItem recordItem) {
        if (log.isTraceEnabled()) {
            log.trace(
                    "Transaction = {}, Record = {}",
                    Utility.printProtoMessage(recordItem.getTransaction()),
                    Utility.printProtoMessage(recordItem.getTransactionRecord()));
        } else if (log.isDebugEnabled()) {
            log.debug("Parsing transaction with consensus timestamp {}", recordItem.getConsensusTimestamp());
        }
    }

    private void recordMetrics(RecordItem recordItem) {
        sizeMetrics
                .getOrDefault(recordItem.getTransactionType(), unknownSizeMetric)
                .record(recordItem.getTransaction().getSerializedSize());

        var consensusTimestamp = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
        latencyMetrics
                .getOrDefault(recordItem.getTransactionType(), unknownLatencyMetric)
                .record(Duration.between(consensusTimestamp, Instant.now()));
    }

    // Correct v5 block numbers once we receive a v6 block with a canonical number
    private void updateIndex(RecordFile recordFile) {
        var lastRecordFile = getLast();

        if (lastRecordFile != null && lastRecordFile.getVersion() < VERSION && recordFile.getVersion() >= VERSION) {
            long offset = recordFile.getIndex() - lastRecordFile.getIndex() - 1;

            if (offset != 0 && streamFileRepository instanceof RecordFileRepository repository) {
                var stopwatch = Stopwatch.createStarted();
                int count = repository.updateIndex(offset);
                log.info("Updated {} blocks with offset {} in {}", count, offset, stopwatch);
            }
        }
    }

    private class RecordItemAggregator implements Consumer<RecordItem> {

        private final LogsBloomAggregator logsBloom = new LogsBloomAggregator();
        private long gasUsed = 0L;

        @Override
        public void accept(RecordItem recordItem) {
            if (!recordItem.isTopLevel()) {
                return;
            }

            var rec = recordItem.getTransactionRecord();
            var result = rec.hasContractCreateResult() ? rec.getContractCreateResult() : rec.getContractCallResult();

            if (ContractFunctionResult.getDefaultInstance().equals(result)) {
                return;
            }

            gasUsed += result.getGasUsed();
            logsBloom.aggregate(DomainUtils.toBytes(result.getBloom()));
        }

        public void update(RecordFile recordFile) {
            recordFile.setGasUsed(gasUsed);
            recordFile.setLoadEnd(System.currentTimeMillis());
            recordFile.setLogsBloom(logsBloom.getBloom());
        }
    }
}
