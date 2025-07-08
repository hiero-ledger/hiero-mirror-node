// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;

import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.entity.EntityId;
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
            0.10.3, 03
            1.2.3, 03
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
}
