// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.hiero.mirror.common.util.DomainUtils.createSha256Digest;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.hedera.hapi.block.stream.output.protoc.BlockFooter;
import com.hedera.hapi.block.stream.protoc.Block;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.hiero.block.internal.protoc.BlockItemUnparsed;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.reader.block.hash.BlockRootHashDigest;

@UtilityClass
public final class BlockStreamTestUtils {

    // The size of block stream hashes, i.e., SHA-256
    public static final int BLOCK_STREAM_HASH_SIZE = 32;

    public static final String SHA_384_BLOCK_STREAMS = "data/blockstreams-sha384";

    // A field unknown to every block item message, as if added in a newer version of the protobuf definitions
    private static final ByteString UNKNOWN_FIELD = UnknownFieldSet.newBuilder()
            .addField(1000, UnknownFieldSet.Field.newBuilder().addVarint(1L).build())
            .build()
            .toByteString();

    /**
     * Gets the directory of the SHA-256 block stream test files. They are converted from the SHA-384 block stream test
     * files in {@link #SHA_384_BLOCK_STREAMS} once per JVM, so only the SHA-384 files are kept in the repository. The
     * directory has the same layout as the SHA-384 one and is deleted on exit.
     *
     * @return the directory of the SHA-256 block stream test files
     */
    public static Path getSha256BlockStreams() {
        return Sha256BlockStreams.PATH;
    }

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

    private static ByteString convertHash(final ByteString hash, final Map<ByteString, ByteString> counterparts) {
        final var counterpart = counterparts.get(hash);
        return counterpart != null
                ? counterpart
                : ByteString.copyFrom(createSha256Digest().digest(hash.toByteArray()));
    }

    private static Block convertToSha256(final Block block, final Map<ByteString, ByteString> counterparts) {
        final var builder = block.toBuilder();
        for (int i = 0; i < builder.getItemsCount(); i++) {
            final var blockItem = builder.getItems(i);
            if (!blockItem.hasBlockFooter()) {
                continue;
            }

            final var blockFooter = blockItem.getBlockFooter();
            builder.setItems(
                    i,
                    blockItem.toBuilder()
                            .setBlockFooter(blockFooter.toBuilder()
                                    .setPreviousBlockRootHash(
                                            convertHash(blockFooter.getPreviousBlockRootHash(), counterparts))
                                    .setRootHashOfAllBlockHashesTree(
                                            convertHash(blockFooter.getRootHashOfAllBlockHashesTree(), counterparts))
                                    .setStartOfBlockStateRootHash(
                                            convertHash(blockFooter.getStartOfBlockStateRootHash(), counterparts))));
        }

        return builder.build();
    }

    @SneakyThrows
    private static Path generateSha256BlockStreams() {
        final var source = TestUtils.getResource(SHA_384_BLOCK_STREAMS).toPath();
        final var parent = Files.createTempDirectory("blockstreams");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtils.deleteQuietly(parent.toFile())));
        final var target = parent.resolve("blockstreams-sha256");

        final var counterparts = new HashMap<ByteString, ByteString>();
        counterparts.put(
                ByteString.copyFrom(createSha384Digest().digest(new byte[] {0x0})),
                ByteString.copyFrom(createSha256Digest().digest(new byte[] {0x0})));

        // The files are sorted by path, i.e., by block number, so a block is converted before the blocks after it
        try (final var paths = Files.walk(source)) {
            for (final var file : paths.filter(Files::isRegularFile).sorted().toList()) {
                final Block block;
                try (final var inputStream = StreamFileData.from(file.toFile()).getInputStream()) {
                    block = Block.parseFrom(inputStream);
                }

                final var converted = convertToSha256(block, counterparts);
                counterparts.put(ByteString.copyFrom(rootHash(block)), ByteString.copyFrom(rootHash(converted)));

                final var targetFile = target.resolve(source.relativize(file));
                Files.createDirectories(targetFile.getParent());
                Files.write(targetFile, TestUtils.zstd(converted.toByteArray()));
            }
        }

        return target;
    }

    private static byte[] rootHash(final Block block) {
        return rootHash(block.getItemsList());
    }

    // Holder so the SHA-256 block stream test files are generated lazily and only once
    private static final class Sha256BlockStreams {
        private static final Path PATH = generateSha256BlockStreams();
    }
}
