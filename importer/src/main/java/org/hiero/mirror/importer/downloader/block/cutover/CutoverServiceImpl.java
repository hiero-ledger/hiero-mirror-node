// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.cutover;

import static java.util.function.Predicate.not;
import static org.hiero.mirror.common.domain.StreamType.BLOCK;
import static org.hiero.mirror.common.domain.StreamType.RECORD;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@CustomLog
@Named
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public final class CutoverServiceImpl implements CutoverService {

    private final BlockProperties blockProperties;
    private final CutoverProperties cutoverProperties;
    private final AtomicReference<Optional<RecordFile>> lastRecordFile = new AtomicReference<>(Optional.empty());
    private final AtomicLong lastSwitchedOrVerified = new AtomicLong(System.currentTimeMillis());
    private final RecordDownloaderProperties recordDownloaderProperties;
    private final RecordFileRepository recordFileRepository;
    private final Stopwatch wrappedRecordBlockStopwatch = Stopwatch.createUnstarted();

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Optional<RecordFile> firstRecordFile = findFirst();

    private boolean blockStreamAdvanced;
    private StreamType currentType = RECORD;
    private @Nullable StreamType lastRunType;
    private @Nullable Long startWrappedRecordBlockConsensusTimestamp;

    @Override
    public synchronized void get(final StreamType streamType, final Runnable task) {
        if (!isActive(streamType)) {
            return;
        }

        lastRunType = streamType;
        final long lastBlockNumber =
                getLastRecordFile().map(RecordFile::getIndex).orElse(-1L);

        try {
            task.run();
        } finally {
            if (streamType == BLOCK) {
                // Track if blocks have been advanced when last run is blockstream, used to fast fallback to
                // recordstream in first-stage
                final long currentBlockNumber =
                        getLastRecordFile().map(RecordFile::getIndex).orElse(-1L);
                blockStreamAdvanced = currentBlockNumber > lastBlockNumber;
            }
        }
    }

    @Override
    public Optional<RecordFile> getLastRecordFile() {
        return Objects.requireNonNull(lastRecordFile.get())
                .or(() -> {
                    final var last = recordFileRepository.findLatest().or(() -> Optional.of(RecordFile.EMPTY));
                    lastRecordFile.compareAndSet(Optional.empty(), last);
                    return last;
                })
                .filter(not(RecordFile::isEmpty));
    }

    @Override
    public void verified(final StreamFile<?> streamFile) {
        if (streamFile instanceof RecordFile recordFile) {
            final var copy = (RecordFile) recordFile.copy();
            copy.clear();
            lastRecordFile.set(Optional.of(copy));
            lastSwitchedOrVerified.set(System.currentTimeMillis());
        }
    }

    private static boolean isBlockStream(final Optional<RecordFile> recordFile) {
        return recordFileMatch(recordFile, v -> v >= BlockStreamReader.VERSION);
    }

    private static boolean isRecordStream(final Optional<RecordFile> recordFile) {
        return recordFileMatch(recordFile, v -> v < BlockStreamReader.VERSION);
    }

    private static boolean recordFileMatch(
            final Optional<RecordFile> recordFile, final Predicate<Integer> versionMatcher) {
        return recordFile.map(RecordFile::getVersion).map(versionMatcher::test).orElse(false);
    }

    private Optional<RecordFile> findFirst() {
        return recordFileRepository.findFirst();
    }

    private boolean hasCutover() {
        final var network =
                recordDownloaderProperties.getCommon().getImporterProperties().getNetwork();
        return cutoverProperties.getEnabled() != null
                ? cutoverProperties.getEnabled()
                : ImporterProperties.HederaNetwork.hasCutover(network);
    }

    private boolean isActive(final StreamType streamType) {
        if (streamType != BLOCK && streamType != RECORD) {
            throw new IllegalArgumentException("StreamType must be BLOCK or RECORD");
        }

        if (!blockProperties.isEnabled() && !recordDownloaderProperties.isEnabled()) {
            // Both are explicitly disabled, skip cutover
            return false;
        }

        if (!hasCutover() || isCutoverComplete()) {
            return streamType == BLOCK ? blockProperties.isEnabled() : recordDownloaderProperties.isEnabled();
        }

        updateActiveStreamType();
        return streamType == currentType;
    }

    private boolean isCutoverComplete() {
        final boolean isLastBlockStream = isBlockStream(getLastRecordFile());
        if (isLastBlockStream) {
            final boolean complete = isRecordStream(getFirstRecordFile());
            if (!blockProperties.isEnabled() && complete) {
                final var network = recordDownloaderProperties
                        .getCommon()
                        .getImporterProperties()
                        .getNetwork();
                log.warn(
                        "Cutover has completed for network {}, please set hiero.mirror.importer.block.enabled=true "
                                + "and restart",
                        network);

                blockProperties.setEnabled(true);
                recordDownloaderProperties.setEnabled(false);
            }
        }

        return isLastBlockStream;
    }

    private void updateActiveStreamType() {
        if (blockProperties.isEnabled()) {
            // Note in config blockstream and recordstream are not allowed to be enabled at the same time, thus
            // this implies recordstream is disabled
            currentType = BLOCK;
            return;
        }

        // When blockstream is disabled, recordstream is enabled, and the network expects a cutover
        var nextType = currentType;
        if (shouldTryFirstStage()) {
            nextType = getNextFirstStageActiveStreamType();
        } else {
            // Single stage cutover
            final long elapsed = System.currentTimeMillis() - lastSwitchedOrVerified.get();
            if (elapsed >= cutoverProperties.getThreshold().toMillis()) {
                nextType = currentType == BLOCK ? RECORD : BLOCK;
                lastSwitchedOrVerified.set(System.currentTimeMillis());
            }
        }

        if (nextType != currentType) {
            log.info("Switching from {} to {}", currentType, nextType);
            currentType = nextType;
        }
    }

    private StreamType getNextFirstStageActiveStreamType() {
        if (currentType == RECORD && lastRunType != null && lastRunType != RECORD) {
            // In case of fallback, ensure recordstream is tried exactly once
            return RECORD;
        }

        final long lastConsensusEnd =
                getLastRecordFile().map(RecordFile::getConsensusEnd).orElse(-1L);
        final StreamType nextType;
        if (lastRunType == BLOCK && !blockStreamAdvanced) {
            // Fast fallback to recordstream if the last run was blockstream and it didn't advance
            nextType = RECORD;
        } else if (startWrappedRecordBlockConsensusTimestamp == null || lastConsensusEnd == -1L) {
            // Force BLOCK to stream WRBs
            nextType = BLOCK;
        } else {
            final var elapsed = wrappedRecordBlockStopwatch.elapsed();
            final var firstStage = cutoverProperties.getFirstStage();
            final long processed = lastConsensusEnd - startWrappedRecordBlockConsensusTimestamp;
            if (elapsed.compareTo(firstStage.getLatencyCheckThreshold()) < 0) {
                // Haven't been streaming WRBs long enough to evaluate latency
                nextType = BLOCK;
            } else {
                // Fallback to recordstream when streaming WRBs exceeds the allowed max latency
                nextType = elapsed.toNanos() > firstStage.getMaxLatency().toNanos() + processed ? RECORD : BLOCK;
            }
        }

        if (nextType == BLOCK) {
            if (startWrappedRecordBlockConsensusTimestamp == null) {
                startWrappedRecordBlockConsensusTimestamp = lastConsensusEnd + 1L;
                wrappedRecordBlockStopwatch.reset().start();
            }
        } else {
            startWrappedRecordBlockConsensusTimestamp = null;
        }

        return nextType;
    }

    private boolean shouldTryFirstStage() {
        final var firstStage = cutoverProperties.getFirstStage();
        if (!firstStage.isEnabled()) {
            return false;
        }

        return getLastRecordFile()
                .map(RecordFile::getHapiVersion)
                .map(version -> version.isGreaterThanOrEqualTo(firstStage.getHapiVersion()))
                .orElse(false);
    }
}
