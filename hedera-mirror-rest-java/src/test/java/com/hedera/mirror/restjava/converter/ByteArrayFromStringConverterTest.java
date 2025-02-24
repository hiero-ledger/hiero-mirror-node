// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ByteArrayFromStringConverterTest {

    @ParameterizedTest(name = "Convert \"{0}\" to EntityId")
    @MethodSource("provideTestCases")
    void testConverter(String source, byte[] expected) {
        var converter = new ByteArrayFromStringConverter();
        assertThat(converter.convert(source)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Convert \"{0}\" to EntityId")
    @NullAndEmptySource
    void testInvalidSource(String source) {
        var converter = new ByteArrayFromStringConverter();
        assertThat(converter.convert(source)).isNull();
    }

    private static Stream<Arguments> provideTestCases() {
        return Stream.of(
                Arguments.of("0", new byte[] {0x30}),
                Arguments.of("01", new byte[] {0x30, 0x31}),
                Arguments.of("hashgraph", "hashgraph".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("", null),
                Arguments.of(" ", new byte[] {0x20}),
                Arguments.of("\t\n", new byte[] {0x09, 0x0a}));
    }
}
