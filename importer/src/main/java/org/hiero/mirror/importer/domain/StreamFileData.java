// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import com.google.common.base.Suppliers;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.CustomLog;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.importer.exception.FileOperationException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.jspecify.annotations.NullMarked;
import org.springframework.util.unit.DataSize;

@CustomLog
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NullMarked
@Value
public class StreamFileData {

    private static final AtomicLong maxDecompressedBytes =
            new AtomicLong(DataSize.ofMegabytes(512).toBytes());

    private static final CompressorStreamFactory compressorStreamFactory = new CompressorStreamFactory(true);

    @EqualsAndHashCode.Include
    private final StreamFilename streamFilename;

    private final Supplier<byte[]> bytes;

    @Getter(lazy = true)
    private final byte[] decompressedBytes = decompressBytes();

    private final Instant lastModified;

    private static StreamFileData readStreamFileData(File file, StreamFilename streamFilename) {
        if (!file.exists() || !file.canRead() || !file.isFile()) {
            throw new FileOperationException("Unable to read file " + file);
        }

        Supplier<byte[]> bytes = Suppliers.memoize(() -> {
            try {
                return FileUtils.readFileToByteArray(file);
            } catch (IOException e) {
                throw new FileOperationException("Unable to read file to byte array", e);
            }
        });

        var lastModified = Instant.ofEpochMilli(file.lastModified());
        return new StreamFileData(streamFilename, bytes, lastModified);
    }

    public static StreamFileData from(File file) {
        return readStreamFileData(file, StreamFilename.from(file.getPath(), File.separator));
    }

    public static StreamFileData from(File file, StreamFilename streamFilename) {
        return readStreamFileData(file, streamFilename);
    }

    public static StreamFileData from(final Path basePath, final StreamFilename streamFilename) {
        final var streamFile = new File(basePath.toFile(), streamFilename.getBucketFilePath());
        return readStreamFileData(streamFile, streamFilename);
    }

    // Used for testing String based files like CSVs
    public static StreamFileData from(String filename, String contents) {
        return new StreamFileData(
                StreamFilename.from(filename), () -> contents.getBytes(StandardCharsets.UTF_8), Instant.now());
    }

    // Used for testing with raw bytes
    public static StreamFileData from(String filename, byte[] bytes) {
        return new StreamFileData(StreamFilename.from(filename), () -> bytes, Instant.now());
    }

    public static void setMaxDecompressedBytes(long bytes) {
        maxDecompressedBytes.set(bytes);
    }

    public byte[] getBytes() {
        return bytes.get();
    }

    public InputStream getInputStream() {
        return new ByteArrayInputStream(getDecompressedBytes());
    }

    public String getFilename() {
        return streamFilename.getFilename();
    }

    public String getFilePath() {
        return streamFilename.getBucketFilePath();
    }

    @Override
    public String toString() {
        return streamFilename.toString();
    }

    private byte[] decompressBytes() {
        var compressor = streamFilename.getCompressor();
        if (StringUtils.isBlank(compressor)) {
            return getBytes();
        }

        var filename = streamFilename.getFilename();
        var decompressionLimit = maxDecompressedBytes.get();
        try (var boundedInputStream = BoundedInputStream.builder()
                .setInputStream(compressorStreamFactory.createCompressorInputStream(
                        compressor, new ByteArrayInputStream(getBytes())))
                .setMaxCount(decompressionLimit + 1)
                .setOnMaxCount((max, count) -> {
                    throw new InvalidStreamFileException("Decompressed size of stream file " + filename
                            + " exceeds the maximum allowed size of " + decompressionLimit + " bytes");
                })
                .get()) {
            return boundedInputStream.readAllBytes();
        } catch (IOException e) {
            log.error("Failed to decompress stream file {}", filename);
            throw new InvalidStreamFileException(filename, e);
        }
    }
}
