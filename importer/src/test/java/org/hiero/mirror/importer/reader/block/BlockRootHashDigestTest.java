// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.reader.block.BlockRootHashDigest.EMPTY_HASH;

import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import org.junit.jupiter.api.Test;

final class BlockRootHashDigestTest {

    @Test
    void digest() {
        // given
        var subject = new BlockRootHashDigest();
        subject.setPreviousHash(EMPTY_HASH);
        subject.setStartOfBlockStateHash(EMPTY_HASH);
        subject.addBlockItem(BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.getDefaultInstance())
                .build());
        subject.addBlockItem(BlockItem.newBuilder()
                .setRoundHeader(RoundHeader.getDefaultInstance())
                .build());
        subject.addBlockItem(BlockItem.newBuilder()
                .setEventHeader(EventHeader.getDefaultInstance())
                .build());
        subject.addBlockItem(BlockItem.newBuilder()
                .setSignedTransaction(SignedTransaction.getDefaultInstance().toByteString())
                .build());
        subject.addBlockItem(BlockItem.newBuilder()
                .setTransactionResult(TransactionResult.getDefaultInstance())
                .build());
        subject.addBlockItem(BlockItem.newBuilder()
                .setStateChanges(StateChanges.getDefaultInstance())
                .build());
        subject.addBlockItem(BlockItem.newBuilder()
                .setBlockProof(BlockProof.getDefaultInstance())
                .build());

        // when
        String actual = subject.digest();

        // then
        assertThat(actual)
                .isEqualTo(
                        "650f9686bfd5374bb8711441e4127165bff805a189ef4fe38b1ba9a5b3b315ed8e325edb049c7a606c6d6a2771b4bf51");

        // digest again
        assertThatThrownBy(subject::digest).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void digestWithEmptyBlock() {
        // given
        var subject = new BlockRootHashDigest();
        subject.setPreviousHash(EMPTY_HASH);
        subject.setStartOfBlockStateHash(EMPTY_HASH);
        subject.addBlockItem(BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.getDefaultInstance())
                .build());
        subject.addBlockItem(BlockItem.newBuilder()
                .setBlockProof(BlockProof.getDefaultInstance())
                .build());

        // when
        String actual = subject.digest();

        // then
        assertThat(actual)
                .isEqualTo(
                        "1d48b117e91005f0301626d27fa66efe0a05047ec7f16fe436912d184c492b4f3ee1dabb876da300aceb35c98ac98b48");
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
