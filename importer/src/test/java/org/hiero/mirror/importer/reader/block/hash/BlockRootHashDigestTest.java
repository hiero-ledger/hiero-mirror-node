// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.hiero.mirror.common.util.DomainUtils.fromBytes;
import static org.hiero.mirror.importer.reader.block.hash.IncrementalStreamingHasherTest.EMPTY_TREE_HASH;

import com.hedera.hapi.block.stream.output.protoc.BlockFooter;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class BlockRootHashDigestTest {

    private static final Timestamp BLOCK_TIMESTAMP =
            Timestamp.newBuilder().setSeconds(1L).build();

    // The root of the reserved slots 8-15
    private static final byte[] EMPTY_HALF = Hex.decode(
            "cf7e7647f57807006f4f5870d2210b5b4038d000b2bfa711bceeb7f4a327346b50c61fda4e5c68110b03ce708fb91cf8");

    @Test
    void digest() {
        // given
        final var digest = new BlockRootHashDigest();
        final byte[] previousRootHash = hashOf((byte) 1);
        final byte[] previousBlocksTreeHash = hashOf((byte) 2);
        final byte[] startOfBlockStateRootHash = hashOf((byte) 3);
        final var blockHeader = blockHeader();
        digest.addBlockItem(blockHeader);
        digest.addBlockItem(blockFooter(previousRootHash, previousBlocksTreeHash, startOfBlockStateRootHash));

        // when
        final var actual = digest.digest();

        // then the block header is the sole output item, so slot 5 is its leaf hash and every other sub-tree is empty
        assertThat(actual)
                .isEqualTo(expectedRootHash(
                        previousRootHash,
                        previousBlocksTreeHash,
                        startOfBlockStateRootHash,
                        EMPTY_TREE_HASH,
                        EMPTY_TREE_HASH,
                        HashUtils.hashLeaf(createSha384Digest(), blockHeader.toByteArray()),
                        EMPTY_TREE_HASH,
                        EMPTY_TREE_HASH));
    }

    @ParameterizedTest(name = "hash {0} has incorrect length")
    @ValueSource(ints = {0, 1, 2})
    void throwWhenIncorrectHashLengthInFooter(final int index) {
        // given
        final var digest = new BlockRootHashDigest();
        final var hashes = new byte[][] {hashOf((byte) 1), hashOf((byte) 2), hashOf((byte) 2)};
        hashes[index] = new byte[index];
        digest.addBlockItem(blockHeader());
        digest.addBlockItem(blockFooter(hashes[0], hashes[1], hashes[2]));

        // when, then
        assertThatThrownBy(digest::digest)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Block root tree slot %d is %d bytes, expected 48".formatted(index, index));
    }

    @ParameterizedTest(name = "root of {0} empty leaves")
    @CsvSource(textBlock = """
            8, cf7e7647f57807006f4f5870d2210b5b4038d000b2bfa711bceeb7f4a327346b50c61fda4e5c68110b03ce708fb91cf8
            16, 5028fe48c7fca408b16bd62b8089c8644be351cbc653e6786136ce144055d18f9495864b270772f664004eed7b97e6b7
            """)
    @Tag("Conformance constants")
    void streamedRootOfEmptySlots(final int count, final String expected) {
        final var emptySlots = Collections.nCopies(count, EMPTY_TREE_HASH);
        assertThat(BlockRootHashDigest.streamedRootOf(emptySlots)).isEqualTo(Hex.decode(expected));
    }

    @Test
    void throwWhenSlotHashIsTruncated() {
        // given
        final var digest = new BlockRootHashDigest();
        digest.addBlockItem(blockHeader());
        digest.addBlockItem(blockFooter(new byte[47], hashOf((byte) 2), hashOf((byte) 3)));

        // when, then
        assertThatThrownBy(digest::digest)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Block root tree slot 0 is 47 bytes, expected 48");
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
        digest.addBlockItem(blockFooter(hashOf((byte) 1), hashOf((byte) 2), hashOf((byte) 3)));

        // when, then
        assertThatThrownBy(digest::digest).isInstanceOf(IllegalStateException.class);
    }

    private static BlockItem blockHeader() {
        return BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder().setBlockTimestamp(BLOCK_TIMESTAMP))
                .build();
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

    private static byte[] hashOf(final byte first) {
        final byte[] hash = new byte[48];
        hash[0] = first;
        return hash;
    }

    /**
     * An independent reference implementation of the block root tree.
     */
    private static byte[] expectedRootHash(final byte[]... slots) {
        final var digest = createSha384Digest();
        final var level = new byte[8][];
        System.arraycopy(slots, 0, level, 0, slots.length);
        Arrays.fill(level, slots.length, level.length, EMPTY_TREE_HASH);
        final byte[] slotTreeRootHash = HashUtils.hashInternalNode(digest, fold(digest, level), EMPTY_HALF);
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
}
