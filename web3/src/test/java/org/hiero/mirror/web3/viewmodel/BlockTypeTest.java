// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static java.lang.Long.MAX_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.hiero.mirror.web3.viewmodel.BlockType.BLOCK_HASH_SENTINEL;

import java.util.Locale;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BlockTypeTest {

    @Test
    void nullOrEmptyStringYieldsLatest() {
        assertThat(BlockType.of(null)).isSameAs(BlockType.LATEST);
        assertThat(BlockType.of("")).isSameAs(BlockType.LATEST);
    }

    @CsvSource({
        "," + MAX_VALUE,
        "''," + MAX_VALUE,
        "0,0",
        "10,10",
        MAX_VALUE + "," + MAX_VALUE,
        "0x0,0",
        "0x1a,26",
        "0x10,16",
        "0XfF,255",
        "0x7fffffffffffffff," + MAX_VALUE,
        "earliest,0",
        "EARLIEST,0",
        "latest," + MAX_VALUE
    })
    @ParameterizedTest
    void valid(String value, long number) {
        final var blockType = BlockType.of(value);
        final var expectedName =
                StringUtils.isNotEmpty(value) ? value.toLowerCase(Locale.ROOT).replace(HEX_PREFIX, "") : "latest";
        assertThat(blockType).isNotNull().returns(expectedName, BlockType::name).returns(number, BlockType::number);
    }

    @CsvSource({"pending", "SAFE", "finalized"})
    @ParameterizedTest
    void tagAliasesResolveToLatestConstant(String value) {
        final var blockType = BlockType.of(value);
        assertThat(blockType).isSameAs(BlockType.LATEST).returns("latest", BlockType::name);
    }

    static Stream<Arguments> blockHashes() {
        final var h64 = "ab".repeat(32);
        final var h96 = "ab".repeat(48);
        return Stream.of(
                Arguments.of(HEX_PREFIX + h64, h64),
                Arguments.of(HEX_PREFIX + h64.toUpperCase(), h64),
                Arguments.of(HEX_PREFIX + h96, h96));
    }

    @MethodSource("blockHashes")
    @ParameterizedTest
    void validBlockHash(String value, String expectedName) {
        final var blockType = BlockType.of(value);
        assertThat(blockType.isHash()).isTrue();
        assertThat(blockType).returns(expectedName, BlockType::name).returns(BLOCK_HASH_SENTINEL, BlockType::number);
    }

    @Test
    void bareShortHexIsBlockNumber() {
        final var blockType = BlockType.of(HEX_PREFIX + "ff");
        assertThat(blockType).returns("ff", BlockType::name).returns(255L, BlockType::number);
    }

    @Test
    void decimalBranchWinsForAllDecimalDigits() {
        assertThat(BlockType.of("10").number()).isEqualTo(10L);
    }

    static Stream<String> invalid() {
        return Stream.of(
                "lastest",
                HEX_PREFIX, // no digits
                "0x-1", // sign inside
                "0xgg", // not hex
                "7f", // no 0x prefix for hex
                HEX_PREFIX + "a".repeat(97), // length 97, no pattern branch
                MAX_VALUE + "1", // 20+ digit string does not use decimal branch, fails matching or parse
                HEX_PREFIX + "a".repeat(16), // hex number branch, overflow on parse
                HEX_PREFIX + "a".repeat(63)); // hex number branch, overflow on parse
    }

    @MethodSource("invalid")
    @ParameterizedTest
    void invalid(String value) {
        assertThatThrownBy(() -> BlockType.of(value))
                .isInstanceOfAny(IllegalArgumentException.class, NumberFormatException.class);
    }

    @ValueSource(strings = {"0x1 ", " 0x1", " latest"})
    @ParameterizedTest
    void invalidNoTrim(String value) {
        assertThatThrownBy(() -> BlockType.of(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
