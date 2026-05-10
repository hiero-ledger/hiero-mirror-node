// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class ZstdCodecTest {

    private final ZstdCodec codec = new ZstdCodec();

    @Test
    void getMessageEncoding() {
        assertThat(codec.getMessageEncoding()).isEqualTo("zstd");
    }

    @ParameterizedTest
    @MethodSource("provideData")
    void roundtrip(byte[] data) throws Exception {
        var compressed = new ByteArrayOutputStream();
        try (var compressor = codec.compress(compressed)) {
            compressor.write(data);
        }

        var decompressed = new ByteArrayOutputStream();
        try (var decompressor = codec.decompress(new ByteArrayInputStream(compressed.toByteArray()))) {
            decompressor.transferTo(decompressed);
        }

        assertThat(decompressed.toByteArray()).isEqualTo(data);
    }

    private static Stream<Arguments> provideData() {
        return Stream.of(
                Arguments.of(new byte[0]),
                Arguments.of("hello world".getBytes(StandardCharsets.UTF_8)),
                Arguments.of(new byte[1000]),
                Arguments.of(new byte[100000]));
    }
}
