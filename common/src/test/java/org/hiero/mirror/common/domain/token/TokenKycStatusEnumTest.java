// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class TokenKycStatusEnumTest {

    @Test
    void dbValues() {
        // The smallint column is persisted as ordinal(); pin it so reordering the constants fails fast.
        assertThat(TokenKycStatusEnum.NOT_APPLICABLE.ordinal()).isZero();
        assertThat(TokenKycStatusEnum.GRANTED.ordinal()).isEqualTo(1);
        assertThat(TokenKycStatusEnum.REVOKED.ordinal()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(TokenKycStatusEnum.class)
    void ordinalRoundTrips(TokenKycStatusEnum value) {
        // Writes persist ordinal(), reads go through fromId(): the two must stay inverse.
        assertThat(TokenKycStatusEnum.fromId(value.ordinal())).isEqualTo(value);
    }

    @Test
    void fromId() {
        assertThat(TokenKycStatusEnum.fromId(1)).isEqualTo(TokenKycStatusEnum.GRANTED);
        assertThat(TokenKycStatusEnum.fromId(-1)).isEqualTo(TokenKycStatusEnum.NOT_APPLICABLE);
    }
}
