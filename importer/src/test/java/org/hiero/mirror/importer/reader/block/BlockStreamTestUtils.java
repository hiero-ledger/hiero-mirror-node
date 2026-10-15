// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.hiero.mirror.common.util.DomainUtils.createSha256Digest;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.protoc.Block;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.reader.block.hash.BlockRootHashDigest;

@UtilityClass
public final class BlockStreamTestUtils {

    // The size of block stream hashes, i.e., SHA-256
    public static final int BLOCK_STREAM_HASH_SIZE = 32;

    public static final String SHA_384_BLOCK_STREAMS = "data/blockstreams-sha384";

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
        final var digest = new BlockRootHashDigest();
        for (final var blockItem : block.getItemsList()) {
            digest.addBlockItem(blockItem);
        }

        return digest.digest();
    }

    // Holder so the SHA-256 block stream test files are generated lazily and only once
    private static final class Sha256BlockStreams {
        private static final Path PATH = generateSha256BlockStreams();
    }
}
