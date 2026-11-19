// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.hedera.hapi.block.stream.output.protoc.BlockFooter;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.hiero.block.internal.protoc.BlockItemUnparsed;
import org.hiero.mirror.importer.reader.block.hash.BlockRootHashDigest;

@UtilityClass
public final class BlockStreamTestUtils {

    public static final String BLOCK_STREAMS = "data/blockstreams";

    // The size of block stream hashes, i.e., SHA-256
    public static final int BLOCK_STREAM_HASH_SIZE = 32;

    // A field unknown to every block item message, as if added in a newer version of the protobuf definitions
    private static final ByteString UNKNOWN_FIELD = UnknownFieldSet.newBuilder()
            .addField(1000, UnknownFieldSet.Field.newBuilder().addVarint(1L).build())
            .build()
            .toByteString();

    /**
     * Calculates the block root hash of the block items, the same way as the block stream reader does.
     *
     * @param blockItems the block items of a block
     * @return the block root hash
     */
    public static byte[] rootHash(final List<BlockItem> blockItems) {
        final var digest = new BlockRootHashDigest();
        var blockTimestamp = Timestamp.getDefaultInstance();
        var blockFooter = BlockFooter.getDefaultInstance();
        for (final var blockItem : blockItems) {
            digest.addBlockItem(toUnparsed(blockItem));
            if (blockItem.hasBlockHeader()) {
                blockTimestamp = blockItem.getBlockHeader().getBlockTimestamp();
            } else if (blockItem.hasBlockFooter()) {
                blockFooter = blockItem.getBlockFooter();
            }
        }

        return digest.digest(blockTimestamp, blockFooter);
    }

    @SneakyThrows
    public static BlockItem toParsed(final BlockItemUnparsed blockItem) {
        return BlockItem.parseFrom(blockItem.toByteArray());
    }

    public static List<BlockItem> toParsed(final List<BlockItemUnparsed> blockItems) {
        return blockItems.stream().map(BlockStreamTestUtils::toParsed).toList();
    }

    @SneakyThrows
    public static BlockItemUnparsed toUnparsed(final BlockItem blockItem) {
        return BlockItemUnparsed.parseFrom(blockItem.toByteArray());
    }

    public static List<BlockItemUnparsed> toUnparsed(final List<BlockItem> blockItems) {
        return blockItems.stream().map(BlockStreamTestUtils::toUnparsed).toList();
    }

    /**
     * Places an unknown field ahead of the known fields of the block item's item, the same as an item serialized by a
     * producer with a newer version of the protobuf definitions. Since the protobuf classes serialize unknown fields
     * after the known fields, re-serializing the parsed block item doesn't reproduce its bytes, as long as the item has
     * a known field with a non-default value.
     *
     * @param blockItem the block item
     * @return the {@link BlockItemUnparsed} with the unknown field
     */
    public static BlockItemUnparsed withUnknownField(final BlockItem blockItem) {
        final var blockItemUnparsed = toUnparsed(blockItem);
        final var field = BlockItemUnparsed.getDescriptor()
                .findFieldByNumber(blockItemUnparsed.getItemCase().getNumber());
        final var item = (ByteString) blockItemUnparsed.getField(field);
        return blockItemUnparsed.toBuilder()
                .setField(field, UNKNOWN_FIELD.concat(item))
                .build();
    }
}
