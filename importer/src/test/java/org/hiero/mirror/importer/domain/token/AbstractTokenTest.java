// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.common.domain.token.Token;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AbstractTokenTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            -100, , -100
            , 500, 500
            , -500, -500
            1200, -500, 700
            """)
    void shouldSetTotalSupplyToNewSupply(Long totalSupply, Long newSupply, Long expected) {
        // given
        // totalSupply will have a null value here
        var token = new Token();

        // when
        // This will set the initial value to totalSupply for the purpose of this test
        token.setTotalSupply(totalSupply);

        // This will be the updated value of totalSupply
        token.setTotalSupply(newSupply);

        // then
        assertThat(token.getTotalSupply()).isEqualTo(expected);
    }
}
