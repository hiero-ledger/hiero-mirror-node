// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.TokenType;
import org.junit.jupiter.api.Test;

class TokenTypeEnumTest {

    @Test
    void fromId() {
        assertThat(TokenTypeEnum.fromId(TokenType.FUNGIBLE_COMMON_VALUE)).isEqualTo(TokenTypeEnum.FUNGIBLE_COMMON);
        assertThat(TokenTypeEnum.fromId(TokenType.NON_FUNGIBLE_UNIQUE_VALUE))
                .isEqualTo(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        assertThat(TokenTypeEnum.fromId(-1)).isEqualTo(TokenTypeEnum.FUNGIBLE_COMMON);
    }
}
