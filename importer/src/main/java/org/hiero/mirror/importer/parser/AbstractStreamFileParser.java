// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.repository.StreamFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public abstract class AbstractStreamFileParser<T extends StreamFile<?>> implements StreamFileParser<T> {

    public static final String STREAM_PARSE_DURATION_METRIC_NAME = "hiero.mirror.importer.parse.duration";

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final MeterRegistry meterRegistry;
    protected final ParserProperties parserProperties;
    protected final StreamFileListener<T> streamFileListener;
    protected final StreamFileRepository<T, Long> streamFileRepository;

    private final AtomicReference<T> last;
    private final Map<StreamType, ParserMetric> parserMetrics = new ConcurrentHashMap<>();

    protected AbstractStreamFileParser(
            MeterRegistry meterRegistry,
            ParserProperties parserProperties,
            StreamFileListener<T> streamFileListener,
            StreamFileRepository<T, Long> streamFileRepository) {
        this.last = new AtomicReference<>();
        this.meterRegistry = meterRegistry;
        this.parserProperties = parserProperties;
        this.streamFileListener = streamFileListener;
        this.streamFileRepository = streamFileRepository;
    }

    @VisibleForTesting
    public void clear() {
        last.set(null);
    }

    @Override
    public ParserProperties getProperties() {
        return parserProperties;
    }

    @Override
    @SuppressWarnings("java:S2139")
    public void parse(final T streamFile) {
        final var parserMetric = getParserMetric(streamFile);
        final var stopwatch = Stopwatch.createStarted();
        boolean success = true;

        try {
            if (!shouldParse(getLast(), streamFile)) {
                streamFile.clear();
                return;
            }

            doParse(streamFile);
            doFlush(streamFile);

            log.info(
                    "Successfully processed {} items from {} in {}",
                    streamFile.getCount(),
                    streamFile.getName(),
                    stopwatch);

            final var latency =
                    Duration.between(Instant.ofEpochSecond(0L, streamFile.getConsensusEnd()), Instant.now());
            if (latency.isPositive()) {
                parserMetric.parseLatencyMetric().record(latency);
            }
            parserMetric
                    .totalDurationMetric()
                    .record(streamFile.getLoadEnd() - streamFile.getLoadStart(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            success = false;
            log.error("Error parsing file {} after {}", streamFile.getName(), stopwatch, e);
            throw e;
        } finally {
            final var timer =
                    success ? parserMetric.parseDurationMetricSuccess() : parserMetric.parseDurationMetricFailure();
            timer.record(stopwatch.elapsed());
        }
    }

    @Override
    @SuppressWarnings("java:S2139")
    public void parse(final List<T> streamFiles) {
        if (CollectionUtils.isEmpty(streamFiles)) {
            return;
        }

        long count = 0L;
        final var initial = getLast();
        var previous = initial;
        int size = streamFiles.size();
        final var stopwatch = Stopwatch.createStarted();
        boolean success = true;
        final var parserMetric = getParserMetric(streamFiles.getFirst());
        T streamFile = null;
        String first = null;

        try {
            for (int i = 0; i < size; ++i) {
                streamFile = streamFiles.get(i);
                if (first == null) {
                    first = streamFile.getName();
                }

                if (!shouldParse(previous, streamFile)) {
                    streamFile.clear();
                    continue;
                }

                doParse(streamFile);

                count += streamFile.getCount();
                previous = streamFile;
            }

            if (initial == previous) {
                return;
            }

            doFlush(previous);
            log.info(
                    "Successfully batch processed {} items from {} files in {}: [{}, {}]",
                    count,
                    size,
                    stopwatch,
                    first,
                    previous.getName());

            final var latency = Duration.between(Instant.ofEpochSecond(0L, previous.getConsensusEnd()), Instant.now());
            if (latency.isPositive()) {
                parserMetric.parseLatencyMetric().record(latency);
            }
            parserMetric
                    .totalDurationMetric()
                    .record(streamFile.getLoadEnd() - streamFile.getLoadStart(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            success = false;
            log.error("Error parsing file {} in {}: ", streamFile != null ? streamFile.getName() : "", stopwatch, e);
            throw e;
        } finally {
            final var timer =
                    success ? parserMetric.parseDurationMetricSuccess() : parserMetric.parseDurationMetricFailure();
            timer.record(stopwatch.elapsed());
        }
    }

    protected abstract void doParse(T streamFile);

    protected final T getLast() {
        var latest = last.get();

        if (latest != null) {
            return latest;
        }

        return streamFileRepository.findLatest().orElse(null);
    }

    protected StreamType getStreamType(final T streamFile) {
        return streamFile.getType();
    }

    private ParserMetric createParserMetric(final StreamType streamType) {
        final var type = streamType.toString();
        final var parseDurationTimerBuilder = Timer.builder(STREAM_PARSE_DURATION_METRIC_NAME)
                .description("The duration in seconds it took to parse the file and store it in the database")
                .tag("type", type);
        final var parseDurationMetricFailure =
                parseDurationTimerBuilder.tag("success", "false").register(meterRegistry);
        final var parseDurationMetricSuccess =
                parseDurationTimerBuilder.tag("success", "true").register(meterRegistry);

        final var parseLatencyMetric = Timer.builder("hiero.mirror.importer.parse.latency")
                .description("The difference in ms between the consensus time of the last transaction in the file "
                        + "and the time at which the file was processed successfully")
                .tag("type", type)
                .register(meterRegistry);
        final var totalDurationMetric = Timer.builder("hiero.mirror.importer.duration")
                .description("The total amount of time the importer took to download and ingest a stream file")
                .tag("type", type)
                .register(meterRegistry);
        return new ParserMetric(
                parseDurationMetricFailure, parseDurationMetricSuccess, parseLatencyMetric, totalDurationMetric);
    }

    private void doFlush(T streamFile) {
        streamFileListener.onEnd(streamFile);
        last.set(streamFile);
        streamFile.clear();
    }

    private ParserMetric getParserMetric(final T streamFile) {
        return parserMetrics.computeIfAbsent(getStreamType(streamFile), this::createParserMetric);
    }

    private boolean shouldParse(T previous, T current) {
        if (!parserProperties.isEnabled()) {
            return false;
        }

        if (previous == null) {
            return true;
        }

        var name = current.getName();

        if (previous.getConsensusEnd() >= current.getConsensusStart()) {
            log.warn("Skipping existing stream file {}", name);
            return false;
        }

        var actualHash = current.getPreviousHash();
        var expectedHash = previous.getHash();

        // Verify hash chain
        if (previous.getType().isChained() && !expectedHash.contentEquals(actualHash)) {
            throw new HashMismatchException(
                    name, expectedHash, actualHash, getClass().getSimpleName());
        }

        return true;
    }

    private record ParserMetric(
            Timer parseDurationMetricFailure,
            Timer parseDurationMetricSuccess,
            Timer parseLatencyMetric,
            Timer totalDurationMetric) {}
}
