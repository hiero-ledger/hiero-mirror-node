// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class TokenFreezeStatusEnumTest {

    @Test
    void dbValues() {
        // The smallint column is persisted as ordinal(); pin it so reordering the constants fails fast.
        assertThat(TokenFreezeStatusEnum.NOT_APPLICABLE.ordinal()).isZero();
        assertThat(TokenFreezeStatusEnum.FROZEN.ordinal()).isEqualTo(1);
        assertThat(TokenFreezeStatusEnum.UNFROZEN.ordinal()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(TokenFreezeStatusEnum.class)
    void ordinalRoundTrips(TokenFreezeStatusEnum value) {
        // Writes persist ordinal(), reads go through fromId(): the two must stay inverse.
        assertThat(TokenFreezeStatusEnum.fromId(value.ordinal())).isEqualTo(value);
    }

    @Test
    void fromId() {
        assertThat(TokenFreezeStatusEnum.fromId(1)).isEqualTo(TokenFreezeStatusEnum.FROZEN);
        assertThat(TokenFreezeStatusEnum.fromId(-1)).isEqualTo(TokenFreezeStatusEnum.NOT_APPLICABLE);
    }
}
