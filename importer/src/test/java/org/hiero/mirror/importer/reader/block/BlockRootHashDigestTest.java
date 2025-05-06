// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

class BlockRootHashDigestTest {

    private static final byte[] EMPTY_HASH = Hex.decode(
            "38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b");

    @Test
    void digest() {
        // given
        var subject = new BlockRootHashDigest();
        subject.setPreviousHash(EMPTY_HASH);
        subject.setStartOfBlockStateHash(EMPTY_HASH);
        subject.addInputBlockItem(BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder().build())
                .build());
        subject.addOutputBlockItem(BlockItem.newBuilder()
                .setStateChanges(StateChanges.newBuilder().build())
                .build());

        // when
        String actual = subject.digest();

        // then
        assertThat(actual)
                .isEqualTo(
                        "4183fa8f91550afb353aaef723a7375e5e8feefc0be04daa8c5d74731c425ddb3d92ef7309f3c71639ad35f7e02e913e");

        // digest again
        assertThatThrownBy(subject::digest).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void digestWithEmptyInputOutputTrees() {
        // given
        var subject = new BlockRootHashDigest();
        subject.setPreviousHash(EMPTY_HASH);
        subject.setStartOfBlockStateHash(EMPTY_HASH);

        // when
        String actual = subject.digest();

        // then
        assertThat(actual)
                .isEqualTo(
                        "f524650830c65a98cda4cbbc9b500c01cfb5aa86225a920b49fe69458ac52aa64e8028a095d5028e363447e27efa31a8");
    }

    @Test
    void digestWithPadding() {
        // given
        var subject = new BlockRootHashDigest();
        subject.setPreviousHash(EMPTY_HASH);
        subject.setStartOfBlockStateHash(EMPTY_HASH);

        var inputBlockItem = BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder().build())
                .build();
        for (int i = 0; i < 3; i++) {
            subject.addInputBlockItem(inputBlockItem);
        }

        var outputBlockItem = BlockItem.newBuilder()
                .setStateChanges(StateChanges.newBuilder().build())
                .build();
        for (int i = 0; i < 11; i++) {
            subject.addOutputBlockItem(outputBlockItem);
        }

        // when
        String actual = subject.digest();

        // then
        assertThat(actual)
                .isEqualTo(
                        "1062c46277c5be0408165dd5eb4aba605b8193066fd66c9f05d92a2ba62150406a897104804e540deb3412657f208f13");
    }

    @Test
    void shouldThrowWhenPreviousHashNotSet() {
        var subject = new BlockRootHashDigest();
        subject.setStartOfBlockStateHash(EMPTY_HASH);
        assertThatThrownBy(subject::digest).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenSetInvalidPreviousHash() {
        var subject = new BlockRootHashDigest();
        assertThatThrownBy(() -> subject.setPreviousHash(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.setPreviousHash(new byte[8])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenSetInvalidStartOfBlockStateHash() {
        var subject = new BlockRootHashDigest();
        assertThatThrownBy(() -> subject.setStartOfBlockStateHash(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.setStartOfBlockStateHash(new byte[10]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenStartOfBlockStateHashNotSet() {
        var subject = new BlockRootHashDigest();
        subject.setPreviousHash(EMPTY_HASH);
        assertThatThrownBy(subject::digest).isInstanceOf(NullPointerException.class);
    }
}
