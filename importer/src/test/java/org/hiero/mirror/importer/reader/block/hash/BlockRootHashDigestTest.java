// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.fromBytes;
import static org.hiero.mirror.importer.reader.block.hash.IncrementalStreamingHasherTest.emptyTreeHash;
import static org.hiero.mirror.importer.reader.block.hash.ShaMessageDigestFactory.SHA_256_SIZE;
import static org.hiero.mirror.importer.reader.block.hash.ShaMessageDigestFactory.SHA_384_SIZE;

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
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class BlockRootHashDigestTest {

    private static final Timestamp BLOCK_TIMESTAMP =
            Timestamp.newBuilder().setSeconds(1L).build();

    // The root of the reserved slots 8-15
    private static final byte[] EMPTY_HALF_SHA_256_HASH =
            Hex.decode("ececb642184ad1483e673c83ad0c6c593976b441d072f90a5323cf3cbfb70197");
    private static final byte[] EMPTY_HALF_SHA_384_HASH = Hex.decode(
            "cf7e7647f57807006f4f5870d2210b5b4038d000b2bfa711bceeb7f4a327346b50c61fda4e5c68110b03ce708fb91cf8");

    @ParameterizedTest(name = "digest size {0}")
    @ValueSource(ints = {SHA_256_SIZE, SHA_384_SIZE})
    void digest(final int digestSize) {
        // given
        final var digest = new BlockRootHashDigest();
        final byte[] previousRootHash = hashOf(digestSize, (byte) 1);
        final byte[] previousBlocksTreeHash = hashOf(digestSize, (byte) 2);
        final byte[] startOfBlockStateRootHash = hashOf(digestSize, (byte) 3);
        final var blockHeader = blockHeader();
        digest.addBlockItem(blockHeader);
        digest.addBlockItem(blockFooter(previousRootHash, previousBlocksTreeHash, startOfBlockStateRootHash));

        // when
        final var actual = digest.digest();

        // then the block header is the sole output item, so slot 5 is its leaf hash and every other sub-tree is empty
        final var emptyTreeHash = emptyTreeHash(digestSize);
        assertThat(actual)
                .hasSize(digestSize)
                .isEqualTo(expectedRootHash(
                        digestSize,
                        previousRootHash,
                        previousBlocksTreeHash,
                        startOfBlockStateRootHash,
                        emptyTreeHash,
                        emptyTreeHash,
                        HashUtils.hashLeaf(
                                ShaMessageDigestFactory.createMessageDigest(digestSize), blockHeader.toByteArray()),
                        emptyTreeHash,
                        emptyTreeHash));
    }

    @ParameterizedTest(name = "digest size {0}")
    @ValueSource(ints = {SHA_256_SIZE, SHA_384_SIZE})
    void digestWithAllSubTrees(final int digestSize) {
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
        final byte[] previousRootHash = hashOf(digestSize, (byte) 1);
        final byte[] previousBlocksTreeHash = hashOf(digestSize, (byte) 2);
        final byte[] startOfBlockStateRootHash = hashOf(digestSize, (byte) 3);

        // the sub-trees interleave in the block, as signed transactions and their results do
        final var digest = new BlockRootHashDigest();
        digest.addBlockItem(blockHeader);
        digest.addBlockItem(roundHeader);
        digest.addBlockItem(eventHeader);
        digest.addBlockItem(signedTransactions[0]);
        digest.addBlockItem(transactionResults[0]);
        digest.addBlockItem(signedTransactions[1]);
        digest.addBlockItem(transactionResults[1]);
        digest.addBlockItem(signedTransactions[2]);
        digest.addBlockItem(stateChanges);
        digest.addBlockItem(traceData);
        digest.addBlockItem(blockFooter(previousRootHash, previousBlocksTreeHash, startOfBlockStateRootHash));

        final var messageDigest = ShaMessageDigestFactory.createMessageDigest(digestSize);
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
        final var actual = digest.digest();

        // then
        assertThat(actual)
                .hasSize(digestSize)
                .isEqualTo(expectedRootHash(
                        digestSize,
                        previousRootHash,
                        previousBlocksTreeHash,
                        startOfBlockStateRootHash,
                        consensusHeadersRootHash,
                        inputRootHash,
                        outputRootHash,
                        leaf(messageDigest, stateChanges),
                        leaf(messageDigest, traceData)));
    }

    @ParameterizedTest(name = "root of {1} empty leaves with digest size {0}")
    @CsvSource(textBlock = """
            32, 8, ececb642184ad1483e673c83ad0c6c593976b441d072f90a5323cf3cbfb70197
            32, 16, 65d350d061d90fe6150bcda9bd28548fb4cdaf4766a15d7e4af5f92f6b7a8202
            48, 8, cf7e7647f57807006f4f5870d2210b5b4038d000b2bfa711bceeb7f4a327346b50c61fda4e5c68110b03ce708fb91cf8
            48, 16, 5028fe48c7fca408b16bd62b8089c8644be351cbc653e6786136ce144055d18f9495864b270772f664004eed7b97e6b7
            """)
    @Tag("Conformance constants")
    void streamedRootOfEmptySlots(final int digestSize, final int count, final String expected) {
        final var emptySlots = Collections.nCopies(count, emptyTreeHash(digestSize));
        assertThat(BlockRootHashDigest.streamedRootOf(digestSize, emptySlots)).isEqualTo(Hex.decode(expected));
    }

    @ParameterizedTest(name = "root hash of all block hashes tree is {0} bytes")
    @ValueSource(ints = {0, 31, 47, 64})
    void throwWhenUnsupportedDigestSize(final int size) {
        // given - the digest size is detected from the root hash of all block hashes tree
        final var digest = new BlockRootHashDigest();
        digest.addBlockItem(blockHeader());
        digest.addBlockItem(blockFooter(
                TestUtils.generateRandomByteArray(size),
                TestUtils.generateRandomByteArray(size),
                TestUtils.generateRandomByteArray(size)));

        // when, then
        assertThatThrownBy(digest::digest)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported digest size " + size);
    }

    @Test
    void throwWithoutBlockFooter() {
        // given
        final var digest = new BlockRootHashDigest();
        digest.addBlockItem(blockHeader());

        // when, then
        assertThatThrownBy(digest::digest).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwWithoutBlockHeader() {
        // given
        final var digest = new BlockRootHashDigest();
        digest.addBlockItem(blockFooter(
                hashOf(SHA_256_SIZE, (byte) 1), hashOf(SHA_256_SIZE, (byte) 2), hashOf(SHA_256_SIZE, (byte) 3)));

        // when, then
        assertThatThrownBy(digest::digest).isInstanceOf(IllegalStateException.class);
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

    private static byte[] emptyHalf(final int digestSize) {
        return switch (digestSize) {
            case SHA_256_SIZE -> EMPTY_HALF_SHA_256_HASH;
            case SHA_384_SIZE -> EMPTY_HALF_SHA_384_HASH;
            default -> throw new IllegalArgumentException("Unsupported digest size " + digestSize);
        };
    }

    /**
     * An independent reference implementation of the block root tree.
     */
    private static byte[] expectedRootHash(final int digestSize, final byte[]... slots) {
        final var digest = ShaMessageDigestFactory.createMessageDigest(digestSize);
        final var level = new byte[8][];
        System.arraycopy(slots, 0, level, 0, slots.length);
        Arrays.fill(level, slots.length, level.length, emptyTreeHash(digestSize));
        final byte[] slotTreeRootHash = HashUtils.hashInternalNode(digest, fold(digest, level), emptyHalf(digestSize));
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

    private static byte[] hashOf(final int digestSize, final byte first) {
        final byte[] hash = new byte[digestSize];
        hash[0] = first;
        return hash;
    }

    private static byte[] leaf(final MessageDigest digest, final BlockItem blockItem) {
        return HashUtils.hashLeaf(digest, blockItem.toByteArray());
    }
}
