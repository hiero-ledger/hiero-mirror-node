// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.Optional;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.reader.record.ProtoRecordFileReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class CutoverServiceTest {

    private static final Duration CUTOVER_THRESHOLD = Duration.ofMillis(200);

    private BlockProperties blockProperties;
    private CutoverService cutoverService;
    private RecordDownloaderProperties recordDownloaderProperties;

    @Mock(strictness = LENIENT)
    private RecordFileRepository recordFileRepository;

    @BeforeEach
    void setup() {
        blockProperties = new BlockProperties();
        blockProperties.setCutoverThreshold(CUTOVER_THRESHOLD);
        final var importerProperties = new ImporterProperties();
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.MAINNET);
        recordDownloaderProperties = new RecordDownloaderProperties(new CommonDownloaderProperties(importerProperties));
        cutoverService = new CutoverServiceImpl(blockProperties, recordDownloaderProperties, recordFileRepository);
    }

    @Test
    void getLastRecordFile() {
        // given
        doReturn(Optional.empty()).when(recordFileRepository).findLatest();

        // when, then
        assertThat(cutoverService.getLastRecordFile()).contains(RecordFile.EMPTY);
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
    void isActiveDuringCutover() {
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
    void isActiveAfterCutover(final boolean isBlockStreamEnabled, final boolean isRecordStreamEnabled) {
        // given
        blockProperties.setEnabled(isBlockStreamEnabled);
        recordDownloaderProperties.setEnabled(isRecordStreamEnabled);
        doReturn(Optional.of(recordFile(0, false))).when(recordFileRepository).findFirst();
        doReturn(Optional.of(recordFile(100, true))).when(recordFileRepository).findLatest();

        // when, then
        assertThat(cutoverService.isActive(StreamType.BLOCK)).isTrue();
        assertThat(cutoverService.isActive(StreamType.RECORD)).isFalse();
        assertThat(blockProperties.isEnabled()).isTrue();
        assertThat(recordDownloaderProperties.isEnabled()).isFalse();
        verify(recordFileRepository).findFirst();
        verify(recordFileRepository).findLatest();
    }

    @Test
    void isActiveAfterCutoverWhenBothDisabled() {
        // given
        blockProperties.setEnabled(false);
        recordDownloaderProperties.setEnabled(false);
        doReturn(Optional.of(recordFile(0, false))).when(recordFileRepository).findFirst();
        doReturn(Optional.of(recordFile(100, true))).when(recordFileRepository).findLatest();

        // when, then
        assertThat(cutoverService.isActive(StreamType.BLOCK)).isFalse();
        assertThat(cutoverService.isActive(StreamType.RECORD)).isFalse();
        verifyNoInteractions(recordFileRepository);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, true
            true, false
            """)
    void isActive(final boolean isBlockStreamEnabled, final boolean isRecordStreamEnabled) {
        // given
        blockProperties.setEnabled(isBlockStreamEnabled);
        recordDownloaderProperties.setEnabled(isRecordStreamEnabled);
        doReturn(Optional.of(recordFile(0, true))).when(recordFileRepository).findFirst();
        doReturn(Optional.of(recordFile(100, true))).when(recordFileRepository).findLatest();

        // when, then
        assertThat(cutoverService.isActive(StreamType.BLOCK)).isEqualTo(isBlockStreamEnabled);
        assertThat(cutoverService.isActive(StreamType.RECORD)).isEqualTo(isRecordStreamEnabled);
        assertThat(blockProperties.isEnabled()).isEqualTo(isBlockStreamEnabled);
        assertThat(recordDownloaderProperties.isEnabled()).isEqualTo(isRecordStreamEnabled);
        verify(recordFileRepository).findFirst();
        verify(recordFileRepository).findLatest();
    }

    @Test
    void isActiveWhenBothDisabled() {
        // given
        blockProperties.setEnabled(false);
        recordDownloaderProperties.setEnabled(false);

        // when, then
        assertThat(cutoverService.isActive(StreamType.BLOCK)).isFalse();
        assertThat(cutoverService.isActive(StreamType.RECORD)).isFalse();
        verifyNoInteractions(recordFileRepository);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, false
            true, false
            """)
    void isActiveDisabled(final boolean isBlockStreamEnabled, final boolean isRecordStreamEnabled) {
        // given
        blockProperties.setEnabled(isBlockStreamEnabled);
        recordDownloaderProperties.setEnabled(isRecordStreamEnabled);
        doReturn(Optional.of(recordFile(100, false))).when(recordFileRepository).findLatest();

        // when
        await().during(CUTOVER_THRESHOLD.multipliedBy(2))
                .pollInterval(Duration.ofMillis(10))
                .until(() -> cutoverService.isActive(StreamType.BLOCK) == isBlockStreamEnabled
                        && cutoverService.isActive(StreamType.RECORD) == isRecordStreamEnabled);
        assertThat(blockProperties.isEnabled()).isEqualTo(isBlockStreamEnabled);
        assertThat(recordDownloaderProperties.isEnabled()).isEqualTo(isRecordStreamEnabled);
    }

    @Test
    void isActiveThrowsWithInvalidType() {
        assertThatThrownBy(() -> cutoverService.isActive(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cutoverService.isActive(StreamType.BALANCE))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(recordFileRepository);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, true, false, false, true, mainnet
            true, false, false, true, false, mainnet
            false, true, , false, true, other
            true, false, , true, false, other
            false, true, false, false, true, other
            true, false, false, true, false, other
            """)
    void isActiveWithoutCutover(
            final boolean isBlockStreamEnabled,
            final boolean isRecordStreamEnabled,
            final Boolean cutoverOverride,
            final boolean expectedShouldGetBlockStream,
            final boolean expectedShouldGetRecordStream,
            final String network) {
        // given
        blockProperties.setEnabled(isBlockStreamEnabled);
        recordDownloaderProperties.getCommon().getImporterProperties().setNetwork(network);
        recordDownloaderProperties.getCommon().setCutover(cutoverOverride);
        recordDownloaderProperties.setEnabled(isRecordStreamEnabled);

        // when, then
        assertThat(cutoverService.isActive(StreamType.BLOCK)).isEqualTo(expectedShouldGetBlockStream);
        assertThat(cutoverService.isActive(StreamType.RECORD)).isEqualTo(expectedShouldGetRecordStream);
        verifyNoInteractions(recordFileRepository);
    }

    private static RecordFile recordFile(long index, boolean isBlockStream) {
        return RecordFile.builder()
                .index(index)
                .version(isBlockStream ? BlockStreamReader.VERSION : ProtoRecordFileReader.VERSION)
                .build();
    }

    private boolean verifyActiveStream(final StreamType activeType) {
        final var inactiveType = activeType == StreamType.BLOCK ? StreamType.RECORD : StreamType.BLOCK;
        return cutoverService.isActive(activeType) && !cutoverService.isActive(inactiveType);
    }
}
