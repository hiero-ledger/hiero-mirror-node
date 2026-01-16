// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import static org.apache.commons.lang3.ObjectUtils.max;
import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.DEMO;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.DATA;

import jakarta.inject.Named;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.exception.InvalidConfigurationException;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.StreamFileRepository;
import org.hiero.mirror.importer.util.Utility;

@CustomLog
@Named
@RequiredArgsConstructor
public final class DateRangeCalculator {

    static final Instant STARTUP_TIME = Instant.now();

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final BlockProperties blockProperties;
    private final ImporterProperties importerProperties;
    private final RecordFileRepository recordFileRepository;

    private final Map<StreamType, DateRangeFilter> filters = new ConcurrentHashMap<>();

    // Clear cache between test runs
    public void clear() {
        filters.clear();
    }

    /**
     * Gets the DateRangeFilter for the downloader (record, balance).
     *
     * @param type - downloader type
     * @return the DateRangeFilter
     */
    public DateRangeFilter getFilter(StreamType type) {
        return filters.computeIfAbsent(type, this::newDateRangeFilter);
    }

    private DateRangeFilter newDateRangeFilter(StreamType streamType) {
        Instant startDate = importerProperties.getStartDate();
        Instant endDate = importerProperties.getEndDate();
        Instant lastFileInstant = findLatest(streamType)
                .map(StreamFile::getConsensusStart)
                .map(nanos -> Instant.ofEpochSecond(0, nanos))
                .orElse(null);
        Instant filterStartDate = lastFileInstant;

        if (startDate != null && startDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format(
                    "Date range constraint violation: " + "startDate (%s) > endDate (%s)", startDate, endDate));
        }

        if (startDate != null) {
            filterStartDate = max(startDate, lastFileInstant);
        } else if (!blockProperties.isEnabled()
                && !DEMO.equalsIgnoreCase(importerProperties.getNetwork())
                && lastFileInstant == null) {
            filterStartDate = STARTUP_TIME;
        }

        DateRangeFilter filter = new DateRangeFilter(filterStartDate, endDate);

        log.info("{}: parser will parse items in {}", streamType, filter);
        return filter;
    }

    /**
     * Gets the latest stream file for downloader based on startDate in ImporterProperties, the startDateAdjustment and
     * last valid downloaded stream file.
     *
     * @param streamType What type of stream to retrieve
     * @return The latest stream file from the database or a dummy stream file if it calculated a different effective
     * start date
     */
    public <T extends StreamFile<?>> Optional<T> getLastStreamFile(StreamType streamType) {
        Instant startDate = importerProperties.getStartDate();
        Optional<T> streamFile = findLatest(streamType);
        Instant lastFileInstant = streamFile
                .map(StreamFile::getConsensusStart)
                .map(nanos -> Instant.ofEpochSecond(0, nanos))
                .orElse(null);

        Instant effectiveStartDate = STARTUP_TIME;
        boolean hasStreamFile = lastFileInstant != null;

        if (startDate != null) {
            effectiveStartDate = max(startDate, hasStreamFile ? lastFileInstant : Instant.EPOCH);
        } else if (hasStreamFile) {
            effectiveStartDate = lastFileInstant;
        } else if (blockProperties.isEnabled() || DEMO.equalsIgnoreCase(importerProperties.getNetwork())) {
            // set effective start date to epoch for blockstream or demo network
            // - blockstream file name doesn't include timestamp
            // - demo network only contains data in the past
            effectiveStartDate = Instant.EPOCH;
        }

        Instant endDate = importerProperties.getEndDate();
        if (startDate != null && startDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format(
                    "Date range constraint violation: " + "startDate (%s) > endDate (%s)", startDate, endDate));
        }

        if (effectiveStartDate.compareTo(endDate) > 0) {
            throw new InvalidConfigurationException(String.format(
                    "Date range constraint violation for %s downloader: effective startDate (%s) > endDate (%s)",
                    streamType, effectiveStartDate, endDate));
        }

        if (!effectiveStartDate.equals(lastFileInstant)) {
            String filename = StreamFilename.getFilename(streamType, DATA, effectiveStartDate);
            T effectiveStreamFile = streamType.newStreamFile();
            effectiveStreamFile.setConsensusStart(DomainUtils.convertToNanosMax(effectiveStartDate));
            effectiveStreamFile.setName(filename);
            effectiveStreamFile.setIndex(streamFile.map(StreamFile::getIndex).orElse(null));
            streamFile = Optional.of(effectiveStreamFile);
        }

        log.info(
                "{}: downloader will download files in time range ({}, {}]",
                streamType,
                effectiveStartDate,
                importerProperties.getEndDate());
        return streamFile;
    }

    @SuppressWarnings("unchecked")
    private <T extends StreamFile<?>> Optional<T> findLatest(StreamType streamType) {
        return (Optional<T>) getStreamFileRepository(streamType).findLatest();
    }

    private StreamFileRepository<?, ?> getStreamFileRepository(StreamType streamType) {
        return switch (streamType) {
            case BALANCE -> accountBalanceFileRepository;
            case RECORD, BLOCK -> recordFileRepository;
        };
    }

    @Value
    public static class DateRangeFilter {
        long start;
        long end;

        public DateRangeFilter(Instant startDate, Instant endDate) {
            if (startDate == null) {
                startDate = Instant.EPOCH;
            }
            start = DomainUtils.convertToNanosMax(startDate);

            if (endDate == null) {
                end = Long.MAX_VALUE;
            } else {
                end = DomainUtils.convertToNanosMax(endDate);
            }
        }

        public static DateRangeFilter all() {
            return new DateRangeFilter(Instant.EPOCH, Utility.MAX_INSTANT_LONG);
        }

        public static DateRangeFilter empty() {
            return new DateRangeFilter(Instant.EPOCH.plusNanos(1), Instant.EPOCH);
        }

        public boolean filter(long timestamp) {
            return timestamp >= start && timestamp <= end;
        }

        @Override
        public String toString() {
            var startInstant = Instant.ofEpochSecond(0, start);
            var endInstant = Instant.ofEpochSecond(0, end);
            return String.format("DateRangeFilter([%s, %s])", startInstant, endInstant);
        }
    }
}
