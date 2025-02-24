// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block;

import static com.hedera.mirror.common.domain.DigestAlgorithm.SHA_384;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.downloader.StreamFileNotifier;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockStreamVerifierTest {

    @Mock(strictness = LENIENT)
    private BlockFileTransformer blockFileTransformer;

    @Mock
    private RecordFileRepository recordFileRepository;

    @Mock
    private StreamFileNotifier streamFileNotifier;

    private RecordFile expectedRecordFile;
    private BlockStreamVerifier verifier;

    @BeforeEach
    void setup() {
        verifier = new BlockStreamVerifier(blockFileTransformer, recordFileRepository, streamFileNotifier);
        expectedRecordFile = RecordFile.builder().build();
        when(blockFileTransformer.transform(any())).thenReturn(expectedRecordFile);
    }

    @Test
    void verifyWithEmptyDb() {
        // given
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());
        var blockFile = getBlockFile(null);

        // then
        assertThat(verifier.getLastBlockNumber()).isEmpty();

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier).verified(expectedRecordFile);
        assertThat(verifier.getLastBlockNumber()).contains(blockFile.getIndex());

        // given next block file
        blockFile = getBlockFile(blockFile);

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier, times(2)).verified(expectedRecordFile);
        assertThat(verifier.getLastBlockNumber()).contains(blockFile.getIndex());
    }

    @Test
    void verifyWithPreviousFileInDb() {
        // given
        var previous = getRecordFile();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(previous));
        var blockFile = getBlockFile(previous);

        // then
        assertThat(verifier.getLastBlockNumber()).contains(previous.getIndex());

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier).verified(expectedRecordFile);
        assertThat(verifier.getLastBlockNumber()).contains(blockFile.getIndex());

        // given next block file
        blockFile = getBlockFile(blockFile);

        // when
        verifier.verify(blockFile);

        // then
        verify(blockFileTransformer).transform(blockFile);
        verify(recordFileRepository).findLatest();
        verify(streamFileNotifier, times(2)).verified(expectedRecordFile);
        assertThat(verifier.getLastBlockNumber()).contains(blockFile.getIndex());
    }

    @Test
    void blockNumberMismatch() {
        // given
        when(recordFileRepository.findLatest()).thenReturn(Optional.empty());
        var blockFile = getBlockFile(null);
        blockFile.setIndex(blockFile.getIndex() + 1);

        // then
        assertThat(verifier.getLastBlockNumber()).isEmpty();

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Block number mismatch");
        verifyNoInteractions(blockFileTransformer);
        verify(recordFileRepository).findLatest();
        verifyNoInteractions(streamFileNotifier);
        assertThat(verifier.getLastBlockNumber()).isEmpty();
    }

    @Test
    void hashMismatch() {
        // given
        var previous = getRecordFile();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(previous));
        var blockFile = getBlockFile(previous);
        blockFile.setPreviousHash(sha384Hash());

        // then
        assertThat(verifier.getLastBlockNumber()).contains(previous.getIndex());

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(HashMismatchException.class)
                .hasMessageContaining("Previous hash mismatch");
        verifyNoInteractions(blockFileTransformer);
        verify(recordFileRepository).findLatest();
        verifyNoInteractions(streamFileNotifier);
        assertThat(verifier.getLastBlockNumber()).contains(previous.getIndex());
    }

    private BlockFile getBlockFile(StreamFile<?> previous) {
        long blockNumber = previous != null ? previous.getIndex() + 1 : DomainUtils.convertToNanosMax(Instant.now());
        String previousHash = previous != null ? previous.getHash() : sha384Hash();
        return BlockFile.builder()
                .hash(sha384Hash())
                .index(blockNumber)
                .name(BlockFile.getBlockStreamFilename(blockNumber))
                .previousHash(previousHash)
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
        return RecordFile.builder().hash(sha384Hash()).index(index).build();
    }

    private String sha384Hash() {
        return DomainUtils.bytesToHex(TestUtils.generateRandomByteArray(SHA_384.getSize()));
    }
}
