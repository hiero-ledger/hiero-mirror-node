// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EvmBlockOverrideTest {

    @Test
    void fromNullIsEmpty() {
        assertThat(EvmBlockOverride.from(null)).isSameAs(EvmBlockOverride.EMPTY);
        assertThat(EvmBlockOverride.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void fromWithoutFieldsIsEmpty() {
        assertThat(EvmBlockOverride.from(new BlockOverride()).isEmpty()).isTrue();
    }

    @Test
    void fromConvertsHeaderFields() {
        final var override = new BlockOverride();
        override.setBlockNumber(80L);
        override.setTime(1_793_619_195L);

        final var result = EvmBlockOverride.from(override);

        assertThat(result.blockNumber()).isEqualTo(80L);
        assertThat(result.timeNanos()).isEqualTo(DomainUtils.convertToNanosMax(1_793_619_195L, 0L));
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void fromSaturatesTimeNanos() {
        final var override = new BlockOverride();
        override.setTime(Long.MAX_VALUE);

        assertThat(EvmBlockOverride.from(override).timeNanos()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void fromConvertsEveryField() {
        final var override = new BlockOverride();
        override.setBaseFeePerGas(Long.MAX_VALUE);
        override.setBlobBaseFee(1L);
        override.setFeeRecipient("0000000000000000000000000000000000000062");
        override.setGasLimit(15_000_000L);
        override.setPrevRandao(123_456_789L);

        final var result = EvmBlockOverride.from(override);

        assertThat(result.baseFee()).isEqualTo(Wei.of(Long.MAX_VALUE));
        assertThat(result.blobBaseFee()).isEqualTo(Wei.ONE);
        assertThat(result.feeRecipient()).isEqualTo(Address.fromHexString("0x62"));
        assertThat(result.gasLimit()).isEqualTo(15_000_000L);
        assertThat(result.prevRandao()).isEqualTo(Bytes32.fromHexString("0x" + "0".repeat(57) + "75bcd15"));
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void fromAllowsZeroForNonNegativeFields() {
        final var override = new BlockOverride();
        override.setBaseFeePerGas(0L);
        override.setBlockNumber(0L);
        override.setPrevRandao(0L);
        override.setTime(0L);

        final var result = EvmBlockOverride.from(override);

        assertThat(result.baseFee()).isEqualTo(Wei.ZERO);
        assertThat(result.blockNumber()).isZero();
        assertThat(result.prevRandao()).isEqualTo(Bytes32.ZERO);
        assertThat(result.timeNanos()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "base_fee_per_gas, -1",
        "blob_base_fee, 0",
        "blob_base_fee, -1",
        "block_number, -1",
        "gas_limit, 0",
        "gas_limit, -1",
        "prev_randao, -1",
        "time, -1",
    })
    void fromRejectsOutOfRangeField(final String field, final long value) {
        final var override = new BlockOverride();
        switch (field) {
            case "base_fee_per_gas" -> override.setBaseFeePerGas(value);
            case "blob_base_fee" -> override.setBlobBaseFee(value);
            case "block_number" -> override.setBlockNumber(value);
            case "gas_limit" -> override.setGasLimit(value);
            case "prev_randao" -> override.setPrevRandao(value);
            case "time" -> override.setTime(value);
            default -> throw new IllegalArgumentException(field);
        }

        assertThatThrownBy(() -> EvmBlockOverride.from(override))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessageContaining("Invalid block_override." + field);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0x62",
                "0x00000000000000000000000000000000000000000062",
                "0x00000000000000000000000000000000000000zz"
            })
    void fromRejectsInvalidFeeRecipient(final String feeRecipient) {
        final var override = new BlockOverride();
        override.setFeeRecipient(feeRecipient);

        assertThatThrownBy(() -> EvmBlockOverride.from(override))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessageContaining("Invalid block_override.fee_recipient");
    }
}
