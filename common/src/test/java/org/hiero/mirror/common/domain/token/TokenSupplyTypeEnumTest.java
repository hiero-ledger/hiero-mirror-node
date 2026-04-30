// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.TokenSupplyType;
import org.junit.jupiter.api.Test;

class TokenSupplyTypeEnumTest {

    @Test
    void fromId() {
        assertThat(TokenSupplyTypeEnum.fromId(TokenSupplyType.FINITE_VALUE)).isEqualTo(TokenSupplyTypeEnum.FINITE);
        assertThat(TokenSupplyTypeEnum.fromId(TokenSupplyType.INFINITE_VALUE)).isEqualTo(TokenSupplyTypeEnum.INFINITE);
        assertThat(TokenSupplyTypeEnum.fromId(-1)).isEqualTo(TokenSupplyTypeEnum.INFINITE);
    }
}
