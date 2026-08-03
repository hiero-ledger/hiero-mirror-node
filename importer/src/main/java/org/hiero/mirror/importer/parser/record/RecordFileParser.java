// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.hiero.mirror.importer.reader.record.ProtoRecordFileReader.VERSION;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.config.DateRangeCalculator;
import org.hiero.mirror.importer.parser.AbstractStreamFileParser;
import org.hiero.mirror.importer.parser.contractlog.EvmAddressCache;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.parser.record.receipt.ReceiptRoot;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.StreamFileRepository;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

@Named
public class RecordFileParser extends AbstractStreamFileParser<RecordFile> {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final DateRangeCalculator dateRangeCalculator;
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;
    private final EvmAddressCache evmAddressCache;
    private final ParserContext parserContext;
    private final RecordItemListener recordItemListener;

    // Metrics
    private final Map<Integer, Timer> latencyMetrics;
    private final Map<Integer, DistributionSummary> sizeMetrics;
    private final Timer unknownLatencyMetric;
    private final DistributionSummary unknownSizeMetric;

    @SuppressWarnings("java:S107")
    public RecordFileParser(
            final ApplicationEventPublisher applicationEventPublisher,
            final DateRangeCalculator dateRangeCalculator,
            final EntityListener entityListener,
            final EntityProperties entityProperties,
            final EvmAddressCache evmAddressCache,
            final MeterRegistry meterRegistry,
            final ParserContext parserContext,
            final RecordParserProperties parserProperties,
            final RecordItemListener recordItemListener,
            final RecordStreamFileListener recordStreamFileListener,
            final StreamFileRepository<RecordFile, Long> streamFileRepository) {
        super(meterRegistry, parserProperties, recordStreamFileListener, streamFileRepository);
        this.applicationEventPublisher = applicationEventPublisher;
        this.dateRangeCalculator = dateRangeCalculator;
        this.entityListener = entityListener;
        this.entityProperties = entityProperties;
        this.evmAddressCache = evmAddressCache;
        this.parserContext = parserContext;
        this.recordItemListener = recordItemListener;

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
            delayString = "#{@recordParserProperties.getRetry().getMinBackoff().toMillis()}",
            excludes = OutOfMemoryError.class,
            maxDelayString = "#{@recordParserProperties.getRetry().getMaxBackoff().toMillis()}",
            maxRetriesString = "#{@recordParserProperties.getRetry().getMaxAttempts() - 1}",
            multiplierString = "#{@recordParserProperties.getRetry().getMultiplier()}")
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
            delayString = "#{@recordParserProperties.getRetry().getMinBackoff().toMillis()}",
            excludes = OutOfMemoryError.class,
            maxDelayString = "#{@recordParserProperties.getRetry().getMaxBackoff().toMillis()}",
            maxRetriesString = "#{@recordParserProperties.getRetry().getMaxAttempts() - 1}",
            multiplierString = "#{@recordParserProperties.getRetry().getMultiplier()}")
    @Transactional(timeoutString = "#{@recordParserProperties.getTransactionTimeout().toSeconds()}")
    public synchronized void parse(List<RecordFile> recordFiles) {
        try {
            super.parse(recordFiles);
        } finally {
            parserContext.clear();
        }
    }

    @Override
    protected void doParse(RecordFile recordFile) {
        var dateRangeFilter = dateRangeCalculator.getFilter(parserProperties.getStreamType());
        var aggregator = new RecordItemAggregator();
        var count = new AtomicLong(0L);
        boolean shouldLog = log.isDebugEnabled() || log.isTraceEnabled();
        final var logIndex = new AtomicInteger(0);
        final var evmTransactionIndex = new AtomicInteger(0);

        applicationEventPublisher.publishEvent(new RecordFileParsedEvent(this, recordFile.getConsensusEnd()));

        parseInitialState(recordFile);
        recordFile.getItems().forEach(recordItem -> {
            if (shouldLog) {
                logItem(recordItem);
            }

            aggregator.accept(recordItem);
            recordItem.setEvmTransactionIndexCounter(evmTransactionIndex);
            setEvmTransactionIndex(recordItem);

            if (dateRangeFilter.filter(recordItem.getConsensusTimestamp())) {
                recordItem.setLogIndex(logIndex);
                recordItemListener.onItem(recordItem);
                aggregator.putReceipt(recordItem);
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

    @Override
    protected StreamType getStreamType(final RecordFile recordFile) {
        return recordFile.getVersion() == BlockStreamReader.VERSION ? StreamType.BLOCK : StreamType.RECORD;
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

    private void parseInitialState(final RecordFile recordFile) {
        final var initialState = recordFile.getInitialState();
        if (initialState == null) {
            return;
        }

        if (entityProperties.getPersist().isContracts()) {
            initialState.contracts().forEach(entityListener::onContract);
        }

        initialState.entities().forEach(entityListener::onEntity);
        initialState.fileDatum().forEach(entityListener::onFileData);
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

    private void setEvmTransactionIndex(RecordItem recordItem) {
        final var type = recordItem.getTransactionType();
        if (type != TransactionType.CONTRACTCALL.getProtoId()
                && type != TransactionType.CONTRACTCREATEINSTANCE.getProtoId()
                && type != TransactionType.ETHEREUMTRANSACTION.getProtoId()) {
            return;
        }

        // WRONG_NONCE transactions never entered EVM execution; no index slot should be assigned
        if (recordItem.getTransactionStatus() == ResponseCodeEnum.WRONG_NONCE_VALUE) {
            return;
        }

        final var contractRelatedParent = recordItem.getContractRelatedParent();
        if (contractRelatedParent != null && contractRelatedParent.getEvmTransactionIndex() != null) {
            recordItem.setEvmTransactionIndex(contractRelatedParent.getEvmTransactionIndex());
        } else if (recordItem.hasContractResult()) {
            recordItem.claimEvmTransactionIndex();
        }
    }

    private final class RecordItemAggregator implements Consumer<RecordItem> {

        private final boolean receiptsEnabled = entityProperties.getPersist().isContractResults();
        private final LogsBloomFilter logsBloom = new LogsBloomFilter();
        private final Map<Long, byte[]> evmAddressById = new HashMap<>();
        private final Set<Long> requestedEvmAddressIds = new HashSet<>();
        private final ReceiptRoot receiptRoot = new ReceiptRoot(evmAddressById);
        private long gasUsed = 0L;
        // Number of ContractLogs already assigned to a receipt; the logs added past it are the current transaction's
        private int assignedContractLogs = 0;

        @Override
        public void accept(RecordItem recordItem) {
            if (!recordItem.isTopLevel() || !recordItem.hasContractResult()) {
                return;
            }

            var rec = recordItem.getTransactionRecord();
            var result = rec.hasContractCreateResult() ? rec.getContractCreateResult() : rec.getContractCallResult();
            gasUsed += result.getGasUsed();
            logsBloom.or(DomainUtils.toBytes(result.getBloom()));
        }

        public void putReceipt(RecordItem recordItem) {
            if (!receiptsEnabled) {
                return;
            }

            final var contractLogs = (List<ContractLog>) parserContext.get(ContractLog.class);
            final var transactionLogs = List.copyOf(contractLogs.subList(assignedContractLogs, contractLogs.size()));
            assignedContractLogs = contractLogs.size();

            if (!recordItem.isTopLevel() || recordItem.getEvmTransactionIndex() == null) {
                return;
            }

            final var contractResult = parserContext.get(ContractResult.class, recordItem.getConsensusTimestamp());
            resolveEvmAddresses(transactionLogs);
            receiptRoot.put(contractResult, transactionLogs, recordItem.getEthereumTransaction());
        }

        public void update(RecordFile recordFile) {
            recordFile.setGasUsed(gasUsed);
            recordFile.setLoadEnd(System.currentTimeMillis());
            recordFile.setLogsBloom(logsBloom.toArrayUnsafe());
            if (receiptsEnabled) {
                recordFile.setReceiptsRoot(receiptRoot.getRootHash());
            }
        }

        private void resolveEvmAddresses(List<ContractLog> contractLogs) {
            for (var contractLog : contractLogs) {
                resolveEvmAddress(contractLog.getContractId());
            }
            for (var entityId : parserContext.getEvmAddressLookupIds()) {
                resolveEvmAddress(EntityId.of(entityId));
            }
        }

        private void resolveEvmAddress(EntityId entityId) {
            // requestedEvmAddressIds guards against re-querying an id (Caffeine doesn't cache alias-less misses)
            if (EntityId.isEmpty(entityId) || !requestedEvmAddressIds.add(entityId.getId())) {
                return;
            }

            var evmAddress = evmAddressCache.get(entityId);
            if (evmAddress == null) {
                // entities created earlier in this batch aren't visible to the cache's backing repository yet
                final var entity = parserContext.get(Entity.class, entityId.getId());
                if (entity != null && !ArrayUtils.isEmpty(entity.getEvmAddress())) {
                    evmAddress = DomainUtils.trim(entity.getEvmAddress());
                    evmAddressCache.put(entityId, evmAddress);
                }
            }

            if (evmAddress != null) {
                evmAddressById.put(entityId.getId(), evmAddress);
            }
        }
    }
}
