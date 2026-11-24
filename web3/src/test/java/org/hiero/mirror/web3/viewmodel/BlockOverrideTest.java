// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.util.DomainUtils;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BlockOverrideTest {

    @Test
    void emptyHasNoValues() {
        final var empty = BlockOverride.EMPTY;

        assertThat(empty.isEmpty()).isTrue();
        assertThat(BlockOverride.builder().build().isEmpty()).isTrue();
        assertThat(empty.baseFeePerGas()).isNull();
        assertThat(empty.blobBaseFee()).isNull();
        assertThat(empty.blockNumber()).isNull();
        assertThat(empty.feeRecipientAddress()).isNull();
        assertThat(empty.gasLimit()).isNull();
        assertThat(empty.prevRandaoBytes()).isNull();
        assertThat(empty.timeNanos()).isNull();
    }

    @Test
    void convertsEveryField() {
        final var override = BlockOverride.builder()
                .baseFeePerGas(Long.MAX_VALUE)
                .blobBaseFee(1L)
                .blockNumber(80L)
                .feeRecipient("0000000000000000000000000000000000000062")
                .gasLimit(15_000_000L)
                .prevRandao(123_456_789L)
                .time(1_793_619_195L)
                .build();

        assertThat(override.isEmpty()).isFalse();
        assertThat(override.baseFeePerGas()).isEqualTo(Long.MAX_VALUE);
        assertThat(override.blobBaseFee()).isEqualTo(1L);
        assertThat(override.blockNumber()).isEqualTo(80L);
        assertThat(override.feeRecipientAddress()).isEqualTo(Address.fromHexString("0x62"));
        assertThat(override.gasLimit()).isEqualTo(15_000_000L);
        assertThat(override.prevRandaoBytes()).isEqualTo(Bytes32.fromHexString("0x" + "0".repeat(57) + "75bcd15"));
        assertThat(override.timeNanos()).isEqualTo(DomainUtils.convertToNanosMax(1_793_619_195L, 0L));
    }

    @Test
    void convertsZeroValues() {
        final var override = BlockOverride.builder()
                .baseFeePerGas(0L)
                .blockNumber(0L)
                .prevRandao(0L)
                .time(0L)
                .build();

        assertThat(override.baseFeePerGas()).isZero();
        assertThat(override.blockNumber()).isZero();
        assertThat(override.prevRandaoBytes()).isEqualTo(Bytes32.ZERO);
        assertThat(override.timeNanos()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankFeeRecipientIsUnset(final String feeRecipient) {
        final var override = BlockOverride.builder().feeRecipient(feeRecipient).build();

        assertThat(override.feeRecipientAddress()).isNull();
    }

    @Test
    void saturatesTimeNanos() {
        final var override = BlockOverride.builder().time(Long.MAX_VALUE).build();

        assertThat(override.timeNanos()).isEqualTo(Long.MAX_VALUE);
    }
}
