// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.common.domain.DigestAlgorithm.SHA_384;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BlockStreamVerifierTest {

    @Mock(strictness = LENIENT)
    private BlockFileTransformer blockFileTransformer;

    @Mock
    private RecordFileRepository recordFileRepository;

    private CutoverService cutoverService;
    private BlockStreamVerifier verifier;

    @BeforeEach
    void setup() {
        cutoverService = spy(new CutoverServiceImpl(
                new BlockProperties(), mock(RecordDownloaderProperties.class), recordFileRepository));
        verifier = new BlockStreamVerifier(
                blockFileTransformer, cutoverService, new SimpleMeterRegistry(), cutoverService);
        when(blockFileTransformer.transform(any(BlockFile.class))).thenAnswer(invocation -> {
            final var blockFile = (BlockFile) invocation.getArgument(0);
            return RecordFile.builder()
                    .consensusEnd(blockFile.getConsensusEnd())
                    .consensusStart(blockFile.getConsensusStart())
                    .index(blockFile.getIndex())
                    .name(blockFile.getName())
                    .build();
        });
    }

    @Test
    void verifyWithEmptyDb() {
        // given
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());
        final var blockFile1 = getBlockFile(null);

        // then
        assertThat(cutoverService.getLastRecordFile()).contains(RecordFile.EMPTY);

        // when
        verifier.verify(blockFile1);

        // then
        verify(blockFileTransformer).transform(blockFile1);
        verify(recordFileRepository).findLatest();
        verify(cutoverService).verified(assertArg(r -> assertRecordFile(r, blockFile1)));
        assertThat(cutoverService.getLastRecordFile()).get().returns(blockFile1.getIndex(), RecordFile::getIndex);

        // given next block file
        final var blockFile2 = getBlockFile(blockFile1);

        // when
        clearInvocations(cutoverService);
        verifier.verify(blockFile2);

        // then
        verify(blockFileTransformer).transform(blockFile2);
        verify(recordFileRepository).findLatest();
        verify(cutoverService).verified(assertArg(r -> assertRecordFile(r, blockFile2)));
        assertThat(cutoverService.getLastRecordFile()).get().returns(blockFile2.getIndex(), RecordFile::getIndex);
    }

    @Test
    void verifyWithPreviousFileInDb() {
        // given
        var previous = getRecordFile();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(previous));
        final var blockFile1 = getBlockFile(previous);

        // then
        assertThat(cutoverService.getLastRecordFile()).get().returns(previous.getIndex(), RecordFile::getIndex);

        // when
        verifier.verify(blockFile1);

        // then
        verify(blockFileTransformer).transform(blockFile1);
        verify(recordFileRepository).findLatest();
        verify(cutoverService).verified(assertArg(r -> assertRecordFile(r, blockFile1)));
        assertThat(cutoverService.getLastRecordFile()).get().returns(blockFile1.getIndex(), RecordFile::getIndex);

        // given next block file
        final var blockFile2 = getBlockFile(blockFile1);

        // when
        clearInvocations(cutoverService);
        verifier.verify(blockFile2);

        // then
        verify(blockFileTransformer).transform(blockFile2);
        verify(recordFileRepository).findLatest();
        verify(cutoverService).verified(assertArg(r -> assertRecordFile(r, blockFile2)));
        assertThat(cutoverService.getLastRecordFile()).get().returns(blockFile2.getIndex(), RecordFile::getIndex);
    }

    @Test
    void blockNumberMismatch() {
        // given
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());
        final var blockFile = getBlockFile(null);
        blockFile.setIndex(blockFile.getIndex() + 1);

        // then
        assertThat(cutoverService.getLastRecordFile()).contains(RecordFile.EMPTY);

        // when, then
        clearInvocations(cutoverService);
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Block number mismatch");
        verifyNoInteractions(blockFileTransformer);
        verify(cutoverService).getLastRecordFile();
        verify(cutoverService, never()).verified(any(RecordFile.class));
        verify(recordFileRepository).findLatest();
        assertThat(cutoverService.getLastRecordFile()).contains(RecordFile.EMPTY);
    }

    @Test
    void hashMismatch() {
        // given
        final var previous = getRecordFile();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(previous));
        final var blockFile = getBlockFile(previous);
        blockFile.setPreviousHash(sha384Hash());

        // then
        assertThat(cutoverService.getLastRecordFile()).get().returns(previous.getIndex(), RecordFile::getIndex);

        // when, then
        clearInvocations(cutoverService);
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(HashMismatchException.class)
                .hasMessageContaining("Previous hash mismatch");
        verifyNoInteractions(blockFileTransformer);
        verify(cutoverService, times(2)).getLastRecordFile();
        verify(cutoverService, never()).verified(any(RecordFile.class));
        verify(recordFileRepository).findLatest();
        assertThat(cutoverService.getLastRecordFile()).get().returns(previous.getIndex(), RecordFile::getIndex);
    }

    @Test
    void malformedFilename() {
        // given
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());
        final var blockFile = getBlockFile(null);
        blockFile.setName("0x01020304.blk.gz");

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Failed to parse block number from filename");
        verifyNoInteractions(blockFileTransformer);
        verify(cutoverService).getLastRecordFile();
        verify(cutoverService, never()).verified(any(RecordFile.class));
        verify(recordFileRepository).findLatest();
    }

    @Test
    void nonConsecutiveBlockNumber() {
        // given
        final var previous = getRecordFile();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(previous));
        final var blockFile = getBlockFile(null);

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Non-consecutive block number");
        verifyNoInteractions(blockFileTransformer);
        verify(cutoverService).getLastRecordFile();
        verify(cutoverService, never()).verified(any(RecordFile.class));
        verify(recordFileRepository).findLatest();
    }

    private void assertRecordFile(final StreamFile<?> actual, final BlockFile source) {
        assertThat(actual)
                .returns(source.getConsensusEnd(), StreamFile::getConsensusEnd)
                .returns(source.getConsensusStart(), StreamFile::getConsensusStart)
                .returns(source.getIndex(), StreamFile::getIndex)
                .returns(source.getName(), StreamFile::getName);
    }

    private BlockFile getBlockFile(StreamFile<?> previous) {
        long blockNumber = previous != null ? previous.getIndex() + 1 : DomainUtils.convertToNanosMax(Instant.now());
        String previousHash = previous != null ? previous.getHash() : sha384Hash();
        long consensusStart = DomainUtils.convertToNanosMax(Instant.now());
        return BlockFile.builder()
                .blockHeader(BlockHeader.newBuilder().build())
                .hash(sha384Hash())
                .index(blockNumber)
                .name(BlockFile.getFilename(blockNumber, true))
                .node("host:port")
                .previousHash(previousHash)
                .consensusStart(consensusStart)
                .consensusEnd(consensusStart + 1)
                .build();
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
