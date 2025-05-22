// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenTest {

    @Test
    void nullCharacters() {
        Token token = new Token();
        token.setName("abc" + (char) 0);
        token.setSymbol("abc" + (char) 0);
        assertThat(token.getName()).isEqualTo("abc�");
        assertThat(token.getSymbol()).isEqualTo("abc�");
    }
}
