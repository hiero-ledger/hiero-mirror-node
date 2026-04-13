// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.cutover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.reader.record.ProtoRecordFileReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@NullUnmarked
@ExtendWith(MockitoExtension.class)
final class CutoverServiceTest {

    private static final Duration CUTOVER_THRESHOLD = Duration.ofMillis(200);

    private BlockProperties blockProperties;
    private CutoverProperties cutoverProperties;
    private CutoverService cutoverService;
    private RecordDownloaderProperties recordDownloaderProperties;

    @Mock
    private Runnable blockStreamTask;

    @Mock
    private Runnable recordStreamTask;

    @Mock(strictness = LENIENT)
    private RecordFileRepository recordFileRepository;

    @BeforeEach
    void setup() {
        final var importerProperties = new ImporterProperties();
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.MAINNET);
        blockProperties = new BlockProperties(importerProperties);
        cutoverProperties = new CutoverProperties();
        cutoverProperties.setEnabled(null);
        cutoverProperties.setThreshold(CUTOVER_THRESHOLD);
        recordDownloaderProperties = new RecordDownloaderProperties(new CommonDownloaderProperties(importerProperties));
        cutoverService = new CutoverServiceImpl(
                blockProperties, cutoverProperties, recordDownloaderProperties, recordFileRepository);
    }

    @Test
    void getLastRecordFile() {
        // given
        doReturn(Optional.empty()).when(recordFileRepository).findLatest();

        // when, then
        assertThat(cutoverService.getLastRecordFile()).isEmpty();
        verify(recordFileRepository).findLatest();

        // when a record file is verified
        var recordFile = recordFile(100, false);
        cutoverService.verified(recordFile);

        // then
        assertThat(cutoverService.getLastRecordFile())
                .get()
                .isEqualTo(recordFile)
                .isNotSameAs(recordFile);
    }

    @Test
    void getDuringCutover() {
        // given
        blockProperties.setEnabled(false);
        recordDownloaderProperties.setEnabled(true);
        doReturn(Optional.of(recordFile(0, false))).when(recordFileRepository).findFirst();
        doReturn(Optional.of(recordFile(100, false))).when(recordFileRepository).findLatest();

        // when threshold hasn't reached, should get record stream
        await().during(CUTOVER_THRESHOLD.dividedBy(2))
                .pollInterval(Duration.ofMillis(10))
                .until(() -> verifyActiveStream(StreamType.RECORD));
        verify(recordFileRepository, times(0)).findFirst();
        verify(recordFileRepository).findLatest();

        // when a new record file is verified, should still get record stream
        cutoverService.verified(recordFile(101, false));
        await().during(CUTOVER_THRESHOLD.dividedBy(2))
                .pollInterval(Duration.ofMillis(10))
                .until(() -> verifyActiveStream(StreamType.RECORD));

        // when threshold has reached, should get block stream
        await().atMost(CUTOVER_THRESHOLD.multipliedBy(2))
                .pollInterval(Duration.ofMillis(10))
                .until(() -> verifyActiveStream(StreamType.BLOCK));

        // when threshold has reached and no block stream available, should get record stream
        await().atMost(CUTOVER_THRESHOLD.multipliedBy(2))
                .pollInterval(Duration.ofMillis(10))
                .until(() -> verifyActiveStream(StreamType.RECORD));

        // switch to block stream again after threshold
        await().atMost(CUTOVER_THRESHOLD.multipliedBy(2))
                .pollInterval(Duration.ofMillis(10))
                .until(() -> verifyActiveStream(StreamType.BLOCK));

        // a block is verified, i.e., cutover happened
        cutoverService.verified(recordFile(102, true));
        await().during(CUTOVER_THRESHOLD.multipliedBy(2))
                .pollInterval(Duration.ofMillis(10))
                .until(() -> verifyActiveStream(StreamType.BLOCK));
        verify(recordFileRepository).findFirst();
        assertThat(blockProperties.isEnabled()).isTrue();
        assertThat(recordDownloaderProperties.isEnabled()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, true
            true, false
            """)
    void getAfterCutover(final boolean isBlockStreamEnabled, final boolean isRecordStreamEnabled) {
        // given
        blockProperties.setEnabled(isBlockStreamEnabled);
        recordDownloaderProperties.setEnabled(isRecordStreamEnabled);
        doReturn(Optional.of(recordFile(0, false))).when(recordFileRepository).findFirst();
        doReturn(Optional.of(recordFile(100, true))).when(recordFileRepository).findLatest();

        // when
        cutoverService.get(StreamType.BLOCK, blockStreamTask);
        cutoverService.get(StreamType.RECORD, recordStreamTask);

        // then
        verify(blockStreamTask).run();
        verifyNoInteractions(recordStreamTask);
        assertThat(blockProperties.isEnabled()).isTrue();
        assertThat(recordDownloaderProperties.isEnabled()).isFalse();
        verify(recordFileRepository).findFirst();
        verify(recordFileRepository).findLatest();
    }

    @Test
    void getAfterCutoverWhenBothDisabled() {
        // given
        blockProperties.setEnabled(false);
        recordDownloaderProperties.setEnabled(false);

        // when
        cutoverService.get(StreamType.BLOCK, blockStreamTask);
        cutoverService.get(StreamType.RECORD, recordStreamTask);

        // then
        verifyNoInteractions(blockStreamTask, recordStreamTask);
        verifyNoInteractions(recordFileRepository);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, true
            true, false
            """)
    void get(final boolean isBlockStreamEnabled, final boolean isRecordStreamEnabled) {
        // given
        blockProperties.setEnabled(isBlockStreamEnabled);
        recordDownloaderProperties.setEnabled(isRecordStreamEnabled);
        doReturn(Optional.of(recordFile(0, true))).when(recordFileRepository).findFirst();
        doReturn(Optional.of(recordFile(100, true))).when(recordFileRepository).findLatest();

        // when
        cutoverService.get(StreamType.BLOCK, blockStreamTask);
        cutoverService.get(StreamType.RECORD, recordStreamTask);

        // then
        verify(blockStreamTask, times(isBlockStreamEnabled ? 1 : 0)).run();
        verify(recordStreamTask, times(isRecordStreamEnabled ? 1 : 0)).run();
        assertThat(blockProperties.isEnabled()).isEqualTo(isBlockStreamEnabled);
        assertThat(recordDownloaderProperties.isEnabled()).isEqualTo(isRecordStreamEnabled);
        verify(recordFileRepository).findFirst();
        verify(recordFileRepository).findLatest();
    }

    @Test
    void getWhenBothDisabled() {
        // given
        blockProperties.setEnabled(false);
        recordDownloaderProperties.setEnabled(false);

        // when
        cutoverService.get(StreamType.BLOCK, blockStreamTask);
        cutoverService.get(StreamType.RECORD, recordStreamTask);

        // then
        verifyNoInteractions(blockStreamTask, recordStreamTask);
        verifyNoInteractions(recordFileRepository);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, false
            true, false
            """)
    void getDisabled(final boolean isBlockStreamEnabled, final boolean isRecordStreamEnabled) {
        // given
        blockProperties.setEnabled(isBlockStreamEnabled);
        recordDownloaderProperties.setEnabled(isRecordStreamEnabled);
        doReturn(Optional.of(recordFile(100, false))).when(recordFileRepository).findLatest();

        // when
        await().during(CUTOVER_THRESHOLD.multipliedBy(2))
                .pollInterval(Duration.ofMillis(10))
                .until(() -> {
                    final var blockRan = new AtomicBoolean(false);
                    final var recordRan = new AtomicBoolean(false);
                    cutoverService.get(StreamType.BLOCK, () -> blockRan.set(true));
                    cutoverService.get(StreamType.RECORD, () -> recordRan.set(true));
                    return blockRan.get() == isBlockStreamEnabled && recordRan.get() == isRecordStreamEnabled;
                });

        // then
        assertThat(blockProperties.isEnabled()).isEqualTo(isBlockStreamEnabled);
        assertThat(recordDownloaderProperties.isEnabled()).isEqualTo(isRecordStreamEnabled);
    }

    @Test
    void getThrowsWithInvalidType() {
        assertThatThrownBy(() -> cutoverService.get(null, () -> {})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cutoverService.get(StreamType.BALANCE, () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(recordFileRepository);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, true, false, 0, 1, mainnet
            true, false, false, 1, 0, mainnet
            false, true, , 0, 1, other
            true, false, , 1, 0, other
            false, true, false, 0, 1, other
            true, false, false, 1, 0, other
            """)
    void getWithoutCutover(
            final boolean isBlockStreamEnabled,
            final boolean isRecordStreamEnabled,
            final Boolean cutoverOverride,
            final int expectedBlockStreamTaskCount,
            final int expectedRecordStreamTaskCount,
            final String network) {
        // given
        cutoverProperties.setEnabled(cutoverOverride);
        blockProperties.setEnabled(isBlockStreamEnabled);
        recordDownloaderProperties.getCommon().getImporterProperties().setNetwork(network);
        recordDownloaderProperties.setEnabled(isRecordStreamEnabled);

        // when
        cutoverService.get(StreamType.BLOCK, blockStreamTask);
        cutoverService.get(StreamType.RECORD, recordStreamTask);

        // then
        verify(blockStreamTask, times(expectedBlockStreamTaskCount)).run();
        verify(recordStreamTask, times(expectedRecordStreamTaskCount)).run();
        verifyNoInteractions(recordFileRepository);
    }

    @ParameterizedTest
    @CsvSource({",", "74"})
    void getFirstStageAlwaysRecordStream(final Integer hapiVersionMinor) {
        // given
        cutoverProperties.setEnabled(true);
        cutoverProperties.getFirstStage().setEnabled(true);

        if (hapiVersionMinor == null) {
            doReturn(Optional.empty()).when(recordFileRepository).findLatest();
        } else {
            final var last = recordFile(100, false);
            last.setHapiVersionMajor(0);
            last.setHapiVersionMinor(hapiVersionMinor);
            last.setHapiVersionPatch(0);
            doReturn(Optional.of(last)).when(recordFileRepository).findLatest();
        }

        // when
        cutoverService.get(StreamType.BLOCK, blockStreamTask);
        cutoverService.get(StreamType.RECORD, recordStreamTask);

        // then
        verifyNoInteractions(blockStreamTask);
        verify(recordStreamTask).run();
    }

    @Test
    void getFirstStageSwitchToBlockStream() {
        // given
        cutoverProperties.setEnabled(true);
        cutoverProperties.getFirstStage().setEnabled(true);

        final var last = recordFile(100, false);
        last.setHapiVersionMajor(0);
        last.setHapiVersionMinor(75);
        last.setHapiVersionPatch(0);
        doReturn(Optional.of(last)).when(recordFileRepository).findLatest();

        final long consensusStart =
                last.getConsensusStart() + Duration.ofMinutes(10).toNanos();
        final var next = last.toBuilder()
                .consensusStart(consensusStart)
                .consensusEnd(consensusStart + 2000L)
                .index(101L)
                .build();

        // when
        cutoverService.get(StreamType.BLOCK, () -> {
            blockStreamTask.run();
            cutoverService.verified(next);
        });
        cutoverService.get(StreamType.RECORD, recordStreamTask);

        // then
        verify(blockStreamTask).run();
        verifyNoInteractions(recordStreamTask);
    }

    @Test
    void getFirstStageSwitchToBlockStreamAndFallback() {
        // given
        cutoverProperties.setEnabled(true);
        final var firstStage = cutoverProperties.getFirstStage();
        firstStage.setEnabled(true);
        firstStage.setMaxLatency(Duration.ofMillis(500));
        firstStage.setLatencyCheckThreshold(Duration.ofSeconds(1));

        final var blockNumber = new AtomicLong(100);
        final var last = recordFile(blockNumber.get(), false);
        last.setHapiVersionMajor(0);
        last.setHapiVersionMinor(75);
        last.setHapiVersionPatch(0);
        doReturn(Optional.of(last)).when(recordFileRepository).findLatest();

        // when
        cutoverService.get(StreamType.BLOCK, blockStreamTask);
        cutoverService.get(StreamType.RECORD, recordStreamTask);

        // then
        verify(blockStreamTask).run();
        verifyNoInteractions(recordStreamTask);

        // when streaming WRBs exceeds latency
        reset(blockStreamTask);
        final var latencyCheckVerificationTimeout =
                firstStage.getLatencyCheckThreshold().plus(Duration.ofMillis(500));
        await().atMost(latencyCheckVerificationTimeout)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    cutoverService.get(StreamType.BLOCK, blockStreamTask);
                    cutoverService.get(StreamType.RECORD, recordStreamTask);
                    verify(blockStreamTask, atLeast(1)).run();
                    verify(recordStreamTask).run();
                });

        // when streaming WRBs again with low latency
        reset(blockStreamTask);
        reset(recordStreamTask);
        final long step = Duration.ofMillis(150).toNanos();
        final var nextConsensusStart = new AtomicLong(last.getConsensusStart() + step);
        await().during(latencyCheckVerificationTimeout)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    cutoverService.get(StreamType.BLOCK, () -> {
                        blockStreamTask.run();

                        final long consensusStart = nextConsensusStart.getAndAdd(step);
                        final var next = last.toBuilder()
                                .consensusStart(consensusStart)
                                .consensusEnd(consensusStart + 1L)
                                .index(blockNumber.getAndIncrement())
                                .build();
                        cutoverService.verified(next);
                    });
                    cutoverService.get(StreamType.RECORD, recordStreamTask);

                    verify(blockStreamTask, atLeast(1)).run();
                    verifyNoInteractions(recordStreamTask);
                });
    }

    @Test
    void getTwoStages() {
        // given
        cutoverProperties.setEnabled(true);
        cutoverProperties.getFirstStage().setEnabled(true);
        doReturn(Optional.of(recordFile(0, false))).when(recordFileRepository).findFirst();

        final var last = recordFile(100, false);
        last.setHapiVersionMajor(0);
        last.setHapiVersionMinor(75);
        last.setHapiVersionPatch(0);
        doReturn(Optional.of(last)).when(recordFileRepository).findLatest();

        final long consensusStart =
                last.getConsensusStart() + Duration.ofMinutes(10).toNanos();
        final var next = last.toBuilder()
                .consensusStart(consensusStart)
                .consensusEnd(consensusStart + 2000L)
                .index(101L)
                .build();

        // when
        cutoverService.get(StreamType.BLOCK, () -> {
            blockStreamTask.run();
            cutoverService.verified(next);
        });
        cutoverService.get(StreamType.RECORD, recordStreamTask);

        // then
        verify(blockStreamTask).run();
        verifyNoInteractions(recordStreamTask);

        // when cutover from recordstream to blockstream happens
        reset(blockStreamTask);
        reset(recordStreamTask);
        final var firstPostCutover = next.toBuilder()
                .consensusStart(next.getConsensusStart() + 2000L)
                .consensusEnd(next.getConsensusStart() + 3000L)
                .index(102L)
                .version(BlockStreamReader.VERSION)
                .build();
        cutoverService.get(StreamType.BLOCK, () -> {
            blockStreamTask.run();
            cutoverService.verified(firstPostCutover);
        });
        cutoverService.get(StreamType.RECORD, recordStreamTask);

        // then
        verify(blockStreamTask).run();
        verifyNoInteractions(recordStreamTask);
        assertThat(blockProperties.isEnabled()).isTrue();
    }

    private static RecordFile recordFile(long index, boolean isBlockStream) {
        final long consensusStart =
                (System.currentTimeMillis() - Duration.ofHours(1).toMillis()) * 1_000_000L;
        return RecordFile.builder()
                .consensusStart(consensusStart)
                .consensusEnd(consensusStart + 2000L)
                .index(index)
                .version(isBlockStream ? BlockStreamReader.VERSION : ProtoRecordFileReader.VERSION)
                .build();
    }

    private boolean verifyActiveStream(final StreamType activeType) {
        final var inactiveType = activeType == StreamType.BLOCK ? StreamType.RECORD : StreamType.BLOCK;
        final var activeRan = new AtomicBoolean(false);
        final var inactiveRan = new AtomicBoolean(false);
        cutoverService.get(activeType, () -> activeRan.set(true));
        cutoverService.get(inactiveType, () -> inactiveRan.set(true));
        return activeRan.get() && !inactiveRan.get();
    }
}
