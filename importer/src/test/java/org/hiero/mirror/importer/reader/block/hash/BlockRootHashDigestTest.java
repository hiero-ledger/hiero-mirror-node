// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.createSha256Digest;
import static org.hiero.mirror.common.util.DomainUtils.fromBytes;
import static org.hiero.mirror.importer.reader.block.BlockStreamTestUtils.toUnparsed;
import static org.hiero.mirror.importer.reader.block.BlockStreamTestUtils.withUnknownField;
import static org.hiero.mirror.importer.reader.block.hash.IncrementalStreamingHasherTest.EMPTY_TREE_HASH;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockFooter;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.trace.protoc.TraceData;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import lombok.SneakyThrows;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class BlockRootHashDigestTest {

    private static final Timestamp BLOCK_TIMESTAMP =
            Timestamp.newBuilder().setSeconds(1L).build();

    // The root of the reserved slots 8-15
    private static final byte[] EMPTY_HALF_HASH =
            Hex.decode("ececb642184ad1483e673c83ad0c6c593976b441d072f90a5323cf3cbfb70197");

    @Test
    void digest() {
        // given
        final byte[] previousRootHash = hashOf((byte) 1);
        final byte[] previousBlocksTreeHash = hashOf((byte) 2);
        final byte[] startOfBlockStateRootHash = hashOf((byte) 3);
        final var blockHeader = blockHeader();
        final var blockFooter = blockFooter(previousRootHash, previousBlocksTreeHash, startOfBlockStateRootHash);
        final var digest = newDigest(blockHeader, blockFooter);

        // when
        final var actual = digest.digest(BLOCK_TIMESTAMP, blockFooter.getBlockFooter());

        // then the block header is the sole output item, so slot 5 is its leaf hash and every other sub-tree is empty
        final var emptyTreeHash = EMPTY_TREE_HASH;
        assertThat(actual)
                .hasSize(DigestAlgorithm.SHA_256.getSize())
                .isEqualTo(expectedRootHash(
                        previousRootHash,
                        previousBlocksTreeHash,
                        startOfBlockStateRootHash,
                        emptyTreeHash,
                        emptyTreeHash,
                        HashUtils.hashLeaf(createSha256Digest(), blockHeader.toByteArray()),
                        emptyTreeHash,
                        emptyTreeHash));
    }

    @Test
    void digestWithAllSubTrees() {
        // given
        final var blockHeader = blockHeader();
        final var roundHeader = BlockItem.newBuilder()
                .setRoundHeader(RoundHeader.newBuilder().setRoundNumber(1L))
                .build();
        final var eventHeader = BlockItem.newBuilder()
                .setEventHeader(EventHeader.getDefaultInstance())
                .build();
        final var signedTransactions = new BlockItem[3];
        final var transactionResults = new BlockItem[2];
        for (int i = 0; i < signedTransactions.length; i++) {
            signedTransactions[i] = BlockItem.newBuilder()
                    .setSignedTransaction(ByteString.copyFrom(new byte[] {(byte) i}))
                    .build();
        }
        for (int i = 0; i < transactionResults.length; i++) {
            transactionResults[i] = BlockItem.newBuilder()
                    .setTransactionResult(TransactionResult.newBuilder()
                            .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(i + 2L)))
                    .build();
        }
        final var stateChanges = BlockItem.newBuilder()
                .setStateChanges(StateChanges.newBuilder()
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(4L)))
                .build();
        final var traceData = BlockItem.newBuilder()
                .setTraceData(TraceData.getDefaultInstance())
                .build();
        final byte[] previousRootHash = hashOf((byte) 1);
        final byte[] previousBlocksTreeHash = hashOf((byte) 2);
        final byte[] startOfBlockStateRootHash = hashOf((byte) 3);

        // the sub-trees interleave in the block, as signed transactions and their results do
        final var blockFooter = blockFooter(previousRootHash, previousBlocksTreeHash, startOfBlockStateRootHash);
        final var digest = newDigest(
                blockHeader,
                roundHeader,
                eventHeader,
                signedTransactions[0],
                transactionResults[0],
                signedTransactions[1],
                transactionResults[1],
                signedTransactions[2],
                stateChanges,
                traceData,
                blockFooter);

        final var messageDigest = createSha256Digest();
        final byte[] consensusHeadersRootHash = HashUtils.hashInternalNode(
                messageDigest, leaf(messageDigest, roundHeader), leaf(messageDigest, eventHeader));
        final byte[] inputRootHash = HashUtils.hashInternalNode(
                messageDigest,
                HashUtils.hashInternalNode(
                        messageDigest,
                        leaf(messageDigest, signedTransactions[0]),
                        leaf(messageDigest, signedTransactions[1])),
                leaf(messageDigest, signedTransactions[2]));
        final byte[] outputRootHash = HashUtils.hashInternalNode(
                messageDigest,
                HashUtils.hashInternalNode(
                        messageDigest, leaf(messageDigest, blockHeader), leaf(messageDigest, transactionResults[0])),
                leaf(messageDigest, transactionResults[1]));

        // when
        final var actual = digest.digest(BLOCK_TIMESTAMP, blockFooter.getBlockFooter());

        // then
        assertThat(actual)
                .hasSize(DigestAlgorithm.SHA_256.getSize())
                .isEqualTo(expectedRootHash(
                        previousRootHash,
                        previousBlocksTreeHash,
                        startOfBlockStateRootHash,
                        consensusHeadersRootHash,
                        inputRootHash,
                        outputRootHash,
                        leaf(messageDigest, stateChanges),
                        leaf(messageDigest, traceData)));
    }

    @SneakyThrows
    @Test
    void digestWithUnknownField() {
        // given a block header with an unknown field ahead of its known fields, so its bytes differ from the bytes of
        // the re-serialized block header
        final var blockHeader = withUnknownField(blockHeader());
        final byte[] blockHeaderBytes = blockHeader.toByteArray();
        assertThat(blockHeaderBytes)
                .isNotEqualTo(BlockItem.parseFrom(blockHeaderBytes).toByteArray());
        final byte[] previousRootHash = hashOf((byte) 1);
        final byte[] previousBlocksTreeHash = hashOf((byte) 2);
        final byte[] startOfBlockStateRootHash = hashOf((byte) 3);
        final var blockFooter = blockFooter(previousRootHash, previousBlocksTreeHash, startOfBlockStateRootHash)
                .getBlockFooter();
        final var digest = new BlockRootHashDigest();
        digest.addBlockItem(blockHeader);

        // when
        final var actual = digest.digest(BLOCK_TIMESTAMP, blockFooter);

        // then the leaf hash of the block header is calculated from its bytes
        final var emptyTreeHash = EMPTY_TREE_HASH;
        assertThat(actual)
                .isEqualTo(expectedRootHash(
                        previousRootHash,
                        previousBlocksTreeHash,
                        startOfBlockStateRootHash,
                        emptyTreeHash,
                        emptyTreeHash,
                        HashUtils.hashLeaf(createSha256Digest(), blockHeaderBytes),
                        emptyTreeHash,
                        emptyTreeHash));
    }

    @ParameterizedTest(name = "root of {0} empty leaves")
    @CsvSource(textBlock = """
            8, ececb642184ad1483e673c83ad0c6c593976b441d072f90a5323cf3cbfb70197
            16, 65d350d061d90fe6150bcda9bd28548fb4cdaf4766a15d7e4af5f92f6b7a8202
            """)
    @Tag("Conformance constants")
    void streamedRootOfEmptySlots(final int count, final String expected) {
        final var emptySlots = Collections.nCopies(count, EMPTY_TREE_HASH);
        assertThat(BlockRootHashDigest.streamedRootOf(emptySlots)).isEqualTo(Hex.decode(expected));
    }

    private static BlockItem blockFooter(
            final byte[] previousRootHash, final byte[] previousBlocksTreeHash, final byte[] startOfBlockStateHash) {
        return BlockItem.newBuilder()
                .setBlockFooter(BlockFooter.newBuilder()
                        .setPreviousBlockRootHash(fromBytes(previousRootHash))
                        .setRootHashOfAllBlockHashesTree(fromBytes(previousBlocksTreeHash))
                        .setStartOfBlockStateRootHash(fromBytes(startOfBlockStateHash)))
                .build();
    }

    private static BlockItem blockHeader() {
        return BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder().setBlockTimestamp(BLOCK_TIMESTAMP))
                .build();
    }

    /**
     * An independent reference implementation of the block root tree.
     */
    private static byte[] expectedRootHash(final byte[]... slots) {
        final var digest = createSha256Digest();
        final var level = new byte[8][];
        System.arraycopy(slots, 0, level, 0, slots.length);
        Arrays.fill(level, slots.length, level.length, EMPTY_TREE_HASH);
        final byte[] slotTreeRootHash = HashUtils.hashInternalNode(digest, fold(digest, level), EMPTY_HALF_HASH);
        return HashUtils.hashInternalNode(
                digest, HashUtils.hashLeaf(digest, BLOCK_TIMESTAMP.toByteArray()), slotTreeRootHash);
    }

    private static byte[] fold(final MessageDigest digest, final byte[][] level) {
        for (int size = level.length; size > 1; size >>= 1) {
            for (int i = 0; i < size >> 1; i++) {
                level[i] = HashUtils.hashInternalNode(digest, level[2 * i], level[2 * i + 1]);
            }
        }

        return level[0];
    }

    private static byte[] hashOf(final byte first) {
        final byte[] hash = new byte[DigestAlgorithm.SHA_256.getSize()];
        hash[0] = first;
        return hash;
    }

    private static byte[] leaf(final MessageDigest digest, final BlockItem blockItem) {
        return HashUtils.hashLeaf(digest, blockItem.toByteArray());
    }

    private static BlockRootHashDigest newDigest(final BlockItem... blockItems) {
        final var digest = new BlockRootHashDigest();
        for (final var blockItem : blockItems) {
            digest.addBlockItem(toUnparsed(blockItem));
        }

        return digest;
    }
}
