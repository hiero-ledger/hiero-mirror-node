// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

final class ClprIdTest {

    private static final String HEX = "1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809";

    @Test
    void valueOf() {
        final var id = ClprId.valueOf("0x" + HEX);
        assertThat(id.bytes()).hasSize(32).startsWith(0x1a, 0x2b).endsWith(0xf8, 0x09);
    }

    @Test
    void valueOfIsCaseInsensitive() {
        assertThat(ClprId.valueOf("0x" + HEX.toUpperCase()).bytes())
                .isEqualTo(ClprId.valueOf("0x" + HEX).bytes());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "0x",
                "0x1234",
                HEX,
                "eq:0x1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809",
                "0x1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f80",
                "0x1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f8091a",
                "0xzz2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809",
            })
    void valueOfInvalid(String input) {
        assertThatThrownBy(() -> ClprId.valueOf(input)).isInstanceOf(RuntimeException.class);
    }
}
