// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

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
                        "a191d2423462908c9a1107a04269121399239b6ac0a21b48100128849ebe36e19212bddbdb08b8466e269ae42119d94e");
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
