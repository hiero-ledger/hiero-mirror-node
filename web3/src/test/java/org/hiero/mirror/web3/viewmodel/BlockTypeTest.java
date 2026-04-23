// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static java.lang.Long.MAX_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.web3.viewmodel.BlockType.BLOCK_HASH_SENTINEL;

import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BlockTypeTest {
    @CsvSource({
        "," + MAX_VALUE,
        "''," + MAX_VALUE,
        "0,0",
        MAX_VALUE + "," + MAX_VALUE,
        "0x0,0",
        "0x1a,26",
        "earliest,0",
        "EARLIEST,0",
        "latest," + MAX_VALUE,
        "latest," + MAX_VALUE
    })
    @ParameterizedTest
    void valid(String value, long number) {
        var blockType = BlockType.of(value);
        var valueLower = StringUtils.isNotEmpty(value) ? value.toLowerCase() : "latest";
        assertThat(blockType).isNotNull().returns(valueLower, BlockType::name).returns(number, BlockType::number);
    }

    @CsvSource({"pending", "PENDING", "safe", "SAFE", "finalized", "FINALIZED"})
    @ParameterizedTest
    void unsupportedDefaultToLatest(String value) {
        var blockType = BlockType.of(value);
        assertThat(blockType).isNotNull().returns(BlockType.LATEST.name(), BlockType::name);
    }

    static Stream<Arguments> validBlockHash() {
        final var hex96 = "ab".repeat(48);
        return Stream.of(Arguments.of(hex96, "0x" + hex96), Arguments.of("0x" + hex96, "0x" + hex96));
    }

    @MethodSource("validBlockHash")
    @ParameterizedTest
    void validBlockHash(String value, String expectedName) {
        var blockType = BlockType.of(value);
        assertThat(blockType.isHash()).isTrue();
        assertThat(blockType).returns(expectedName, BlockType::name).returns(BLOCK_HASH_SENTINEL, BlockType::number);
    }

    @ValueSource(
            strings = {
                MAX_VALUE + "1",
                "0xabcdefghijklmnopqrstuvwxyz",
                "abcdefghijklmnopqrstuvwxyz",
                "lastest",
                "-1",
                "0x-1",
                "-100",
                "-0x64",
                "0x-64",
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            })
    @ParameterizedTest
    void invalid(String value) {
        assertThatThrownBy(() -> BlockType.of(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
