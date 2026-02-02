// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

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
import org.hiero.mirror.importer.downloader.BatchStreamFileNotifier;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Primary;

@CustomLog
@Named
@NullMarked
@Primary
@RequiredArgsConstructor
public final class CutoverServiceImpl implements CutoverService {

    private static final RecordFile EMPTY = new RecordFile();

    private final BatchStreamFileNotifier batchStreamFileNotifier;
    private final BlockProperties blockProperties;
    private final AtomicLong lastSwitchedOrVerified = new AtomicLong();
    private final AtomicReference<Optional<RecordFile>> lastRecordFile = new AtomicReference<>(Optional.empty());
    private final RecordDownloaderProperties recordDownloaderProperties;
    private final RecordFileRepository recordFileRepository;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Optional<RecordFile> firstRecordFile = findFirst();

    private boolean configWarningLogged = false;
    private StreamType currentType = RECORD;

    @Override
    public synchronized boolean shouldGetStream(final StreamType streamType) {
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
            if (!blockProperties.isEnabled() && !configWarningLogged && isCutoverCompleted) {
                final var network = recordDownloaderProperties
                        .getCommon()
                        .getImporterProperties()
                        .getNetwork();
                log.warn(
                        "Cutover has completed for network {}, please set hiero.mirror.importer.block.enabled=true and restart",
                        network);
                configWarningLogged = true;

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
            if (elapsed
                    >= recordDownloaderProperties
                            .getCommon()
                            .getCutoverThreshold()
                            .toMillis()) {
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
            lastRecordFile.set(Optional.of(recordFile));
            lastSwitchedOrVerified.set(System.currentTimeMillis());
        }

        batchStreamFileNotifier.verified(streamFile);
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
                .filter(r -> r != EMPTY)
                .map(RecordFile::getVersion)
                .map(versionMatcher::test)
                .orElse(false);
    }

    private Optional<RecordFile> findFirst() {
        return recordFileRepository.findFirst();
    }

    private Optional<RecordFile> getLastRecordFile() {
        return Objects.requireNonNull(lastRecordFile.get()).or(() -> {
            final var last = recordFileRepository.findLatest().or(() -> Optional.of(EMPTY));
            lastRecordFile.set(last);
            return last;
        });
    }

    private boolean hasCutover() {
        final var common = recordDownloaderProperties.getCommon();
        final var network = common.getImporterProperties().getNetwork();
        return common.getCutover() != null ? common.getCutover() : ImporterProperties.HederaNetwork.hasCutover(network);
    }
}
