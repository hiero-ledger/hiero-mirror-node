// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SyntheticContractLogTest {

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            0.0.0, ''
            0.0.3, 03
            0.10.3, 0a0000000000000003
            1.2.3, 0100000000000000020000000000000003
            """)
    void entityIdToBytes(String entityIdStr, String expectedHex) {
        var entityId = EntityId.of(entityIdStr);
        byte[] expected = Hex.decode(expectedHex);
        assertThat(AbstractSyntheticContractLog.entityIdToBytes(entityId)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            0, ''
            128, 80
            9223372036854775807, 7fffffffffffffff
            """)
    void longToBytes(long value, String expectedHex) {
        byte[] expected = Hex.decode(expectedHex);
        assertThat(AbstractSyntheticContractLog.longToBytes(value)).isEqualTo(expected);
    }

    @Test
    void booleanToBytes() {
        assertThat(AbstractSyntheticContractLog.booleanToBytes(true)).isEqualTo(new byte[] {1});
        assertThat(AbstractSyntheticContractLog.booleanToBytes(false)).isEqualTo(new byte[] {0});
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            12345600, 12345600
            00123400, 123400
            00003400, 3400
            00000000, ''
            '', ''
            """)
    void trim(String inputHex, String expectedHex) {
        byte[] input = Hex.decode(inputHex);
        byte[] expected = Hex.decode(expectedHex);
        assertThat(AbstractSyntheticContractLog.trim(input)).isEqualTo(expected);
    }

    @Test
    void trimNull() {
        assertThat(AbstractSyntheticContractLog.trim(null)).isEqualTo(null);
    }
}
