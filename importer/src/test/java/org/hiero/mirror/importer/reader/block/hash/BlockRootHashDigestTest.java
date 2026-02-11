// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.fromBytes;

import com.hedera.hapi.block.stream.output.protoc.BlockFooter;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.Test;

final class BlockRootHashDigestTest {

    @Test
    void digest() {
        // given
        final var digest = new BlockRootHashDigest();
        final byte[] previousRootHash = new byte[48];
        previousRootHash[0] = 1;
        final byte[] previousBlocksTreeHash = new byte[48];
        previousBlocksTreeHash[0] = 2;
        final byte[] startOfBlockStateRootHash = new byte[48];
        startOfBlockStateRootHash[0] = 3;
        digest.addBlockItem(BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder()
                        .setBlockTimestamp(Timestamp.newBuilder().setSeconds(1L)))
                .build());
        digest.addBlockItem(BlockItem.newBuilder()
                .setBlockFooter(BlockFooter.newBuilder()
                        .setPreviousBlockRootHash(fromBytes(previousRootHash))
                        .setRootHashOfAllBlockHashesTree(fromBytes(previousBlocksTreeHash))
                        .setStartOfBlockStateRootHash(fromBytes(startOfBlockStateRootHash)))
                .build());

        // when
        final var actual = digest.digest();

        // then
        assertThat(actual)
                .isEqualTo(
                        "9c84abc8b725bc38f73b86b207ede3b9eb6772c49dd87191100d0108305e5b4db6e0eabea7fcf39f16baf8921bf1dc20");
    }

    @Test
    void throwWithoutBlockFooter() {
        // given
        final var digest = new BlockRootHashDigest();
        digest.addBlockItem(BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder()
                        .setBlockTimestamp(Timestamp.newBuilder().setSeconds(1L)))
                .build());

        // when, then
        assertThatThrownBy(digest::digest).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwWithoutBlockHeader() {
        // given
        final var digest = new BlockRootHashDigest();
        final byte[] previousRootHash = new byte[48];
        previousRootHash[0] = 1;
        final byte[] previousBlocksTreeHash = new byte[48];
        previousBlocksTreeHash[0] = 2;
        final byte[] startOfBlockStateRootHash = new byte[48];
        startOfBlockStateRootHash[0] = 3;
        digest.addBlockItem(BlockItem.newBuilder()
                .setBlockFooter(BlockFooter.newBuilder()
                        .setPreviousBlockRootHash(fromBytes(previousRootHash))
                        .setRootHashOfAllBlockHashesTree(fromBytes(previousBlocksTreeHash))
                        .setStartOfBlockStateRootHash(fromBytes(startOfBlockStateRootHash)))
                .build());

        // when, then
        assertThatThrownBy(digest::digest).isInstanceOf(IllegalStateException.class);
    }
}
