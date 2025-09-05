// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.domain.DigestAlgorithm.SHA_384;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockStreamVerifierTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock(strictness = LENIENT)
    private BlockFileTransformer blockFileTransformer;

    @Mock
    private RecordFileRepository recordFileRepository;

    @Mock
    private StreamFileNotifier streamFileNotifier;

    private CommonDownloaderProperties commonDownloaderProperties;
    private RecordFile expectedRecordFile;
    private BlockStreamVerifier verifier;

    @BeforeEach
    void setup() {
        commonDownloaderProperties = new CommonDownloaderProperties(new ImporterProperties());
        var meterRegistry = new SimpleMeterRegistry();
        verifier = new BlockStreamVerifier(
                blockFileTransformer,
                commonDownloaderProperties,
                recordFileRepository,
                streamFileNotifier,
                meterRegistry);
        expectedRecordFile = RecordFile.builder().build();
        when(blockFileTransformer.transform(any())).thenReturn(expectedRecordFile);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            ,,0
            2,,2
            ,1,2
            4,1,2
            """)
    void getNextBlockNumber(Long startBlockNumber, Long latestBlockNumberInDb, long expected) {
        if (startBlockNumber != null) {
            commonDownloaderProperties.getImporterProperties().setStartBlockNumber(startBlockNumber);
        }

        if (latestBlockNumberInDb != null) {
            doReturn(Optional.of(domainBuilder
                            .recordFile()
                            .customize(rf -> rf.index(latestBlockNumberInDb))
                            .get()))
                    .when(recordFileRepository)
                    .findLatest();
        } else {
            doReturn(Optional.empty()).when(recordFileRepository).findLatest();
        }

        assertThat(verifier.getNextBlockNumber()).isEqualTo(expected);
    }

    @Test
    void getNextBlockNumberAfterVerified() {
        var blockFile = getBlockFile(null);
        verifier.verify(blockFile);
        assertThat(verifier.getNextBlockNumber()).isEqualTo(blockFile.getIndex() + 1);
    }

    @Test
    void verifyWithEmptyDb() {
        // given
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());
        var blockFile = getBlockFile(null);

        // then
        assertThat(verifier.getLastBlockFile()).contains(BlockStreamVerifier.EMPTY);

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier).verified(expectedRecordFile);
        assertThat(verifier.getLastBlockFile()).get().returns(blockFile.getIndex(), BlockFile::getIndex);

        // given next block file
        blockFile = getBlockFile(blockFile);

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier, times(2)).verified(expectedRecordFile);
        assertThat(verifier.getLastBlockFile()).get().returns(blockFile.getIndex(), BlockFile::getIndex);
    }

    @Test
    void verifyWithPreviousFileInDb() {
        // given
        var previous = getRecordFile();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(previous));
        var blockFile = getBlockFile(previous);

        // then
        assertThat(verifier.getLastBlockFile()).get().returns(previous.getIndex(), BlockFile::getIndex);

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier).verified(expectedRecordFile);
        assertThat(verifier.getLastBlockFile()).get().returns(blockFile.getIndex(), BlockFile::getIndex);

        // given next block file
        blockFile = getBlockFile(blockFile);

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier, times(2)).verified(expectedRecordFile);
        assertThat(verifier.getLastBlockFile()).get().returns(blockFile.getIndex(), BlockFile::getIndex);
    }

    @Test
    void blockNumberMismatch() {
        // given
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());
        var blockFile = getBlockFile(null);
        blockFile.setIndex(blockFile.getIndex() + 1);

        // then
        assertThat(verifier.getLastBlockFile()).contains(BlockStreamVerifier.EMPTY);

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Block number mismatch");
        verifyNoInteractions(blockFileTransformer);
        verify(recordFileRepository).findLatest();
        verifyNoInteractions(streamFileNotifier);
        assertThat(verifier.getLastBlockFile()).contains(BlockStreamVerifier.EMPTY);
    }

    @Test
    void hashMismatch() {
        // given
        var previous = getRecordFile();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(previous));
        var blockFile = getBlockFile(previous);
        blockFile.setPreviousHash(sha384Hash());

        // then
        assertThat(verifier.getLastBlockFile()).get().returns(previous.getIndex(), BlockFile::getIndex);

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(HashMismatchException.class)
                .hasMessageContaining("Previous hash mismatch");
        verifyNoInteractions(blockFileTransformer);
        verify(recordFileRepository).findLatest();
        verifyNoInteractions(streamFileNotifier);
        assertThat(verifier.getLastBlockFile()).get().returns(previous.getIndex(), BlockFile::getIndex);
    }

    private BlockFile getBlockFile(StreamFile<?> previous) {
        long blockNumber = previous != null ? previous.getIndex() + 1 : DomainUtils.convertToNanosMax(Instant.now());
        String previousHash = previous != null ? previous.getHash() : sha384Hash();
        long consensusStart = DomainUtils.convertToNanosMax(Instant.now());
        return BlockFile.builder()
                .hash(sha384Hash())
                .index(blockNumber)
                .name(BlockFile.getFilename(blockNumber, true))
                .previousHash(previousHash)
                .consensusStart(consensusStart)
                .build();
    }

    @Test
    void malformedFilename() {
        // given
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());
        var blockFile = getBlockFile(null);
        blockFile.setName("0x01020304.blk.gz");

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Failed to parse block number from filename");
        verifyNoInteractions(blockFileTransformer);
        verify(recordFileRepository).findLatest();
        verifyNoInteractions(streamFileNotifier);
    }

    @Test
    void nonConsecutiveBlockNumber() {
        // given
        var previous = getRecordFile();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(previous));
        var blockFile = getBlockFile(null);

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Non-consecutive block number");
        verifyNoInteractions(blockFileTransformer);
        verify(recordFileRepository).findLatest();
        verifyNoInteractions(streamFileNotifier);
    }

    private RecordFile getRecordFile() {
        long index = DomainUtils.convertToNanosMax(Instant.now());
        long consensusStart = DomainUtils.convertToNanosMax(Instant.now());
        return RecordFile.builder()
                .hash(sha384Hash())
                .index(index)
                .consensusStart(consensusStart)
                .build();
    }

    private String sha384Hash() {
        return DomainUtils.bytesToHex(TestUtils.generateRandomByteArray(SHA_384.getSize()));
    }
}
