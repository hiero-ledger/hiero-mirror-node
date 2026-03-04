// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static java.util.function.Predicate.not;
import static org.hiero.mirror.common.domain.StreamType.BLOCK;
import static org.hiero.mirror.common.domain.StreamType.RECORD;

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
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@CustomLog
@Named
@NullMarked
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
final class CutoverServiceImpl implements CutoverService {

    private final BlockProperties blockProperties;
    private final AtomicReference<Optional<RecordFile>> lastRecordFile = new AtomicReference<>(Optional.empty());
    private final AtomicLong lastSwitchedOrVerified = new AtomicLong();
    private final RecordDownloaderProperties recordDownloaderProperties;
    private final RecordFileRepository recordFileRepository;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Optional<RecordFile> firstRecordFile = findFirst();

    private StreamType currentType = RECORD;

    @Override
    public Optional<RecordFile> getLastRecordFile() {
        return Objects.requireNonNull(lastRecordFile.get()).or(() -> {
            final var last = recordFileRepository.findLatest().or(() -> Optional.of(RecordFile.EMPTY));
            lastRecordFile.compareAndSet(Optional.empty(), last);
            return last;
        });
    }

    @Override
    public synchronized boolean isActive(final StreamType streamType) {
        if (streamType != BLOCK && streamType != RECORD) {
            throw new IllegalArgumentException("StreamType must be BLOCK or RECORD");
        }

        if (!hasCutover()) {
            return streamType == BLOCK ? blockProperties.isEnabled() : recordDownloaderProperties.isEnabled();
        }

        if (!blockProperties.isEnabled() && !recordDownloaderProperties.isEnabled()) {
            // Both are explicitly disabled, skip cutover
            return false;
        }

        lastSwitchedOrVerified.compareAndExchange(0, System.currentTimeMillis());
        final boolean isLastBlockStream = isBlockStream(getLastRecordFile());
        if (isLastBlockStream) {
            final boolean isCutoverCompleted = isRecordStream(getFirstRecordFile());
            if (!blockProperties.isEnabled() && isCutoverCompleted) {
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

            return streamType == BLOCK ? blockProperties.isEnabled() : recordDownloaderProperties.isEnabled();
        } else {
            if (blockProperties.isEnabled()) {
                // Note in config blockstream and recordstream are not allowed to be enabled at the same time, thus
                // this implies recordstream is disabled
                return streamType == BLOCK;
            }

            // When blockstream is disabled, recordstream is enabled, and the network expects a cutover
            final long elapsed = System.currentTimeMillis() - lastSwitchedOrVerified.get();
            if (elapsed >= blockProperties.getCutoverThreshold().toMillis()) {
                final var nextType = currentType == BLOCK ? RECORD : BLOCK;
                log.info("Switching from {} to {}", currentType, nextType);
                currentType = nextType;
                lastSwitchedOrVerified.set(System.currentTimeMillis());
            }

            return streamType == currentType;
        }
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
        return recordFile
                .filter(not(RecordFile::isEmpty))
                .map(RecordFile::getVersion)
                .map(versionMatcher::test)
                .orElse(false);
    }

    private Optional<RecordFile> findFirst() {
        return recordFileRepository.findFirst();
    }

    private boolean hasCutover() {
        final var network =
                recordDownloaderProperties.getCommon().getImporterProperties().getNetwork();
        return blockProperties.getCutover() != null
                ? blockProperties.getCutover()
                : ImporterProperties.HederaNetwork.hasCutover(network);
    }
}
