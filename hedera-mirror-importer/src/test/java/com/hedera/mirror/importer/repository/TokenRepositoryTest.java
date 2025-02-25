// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenRepositoryTest extends ImporterIntegrationTest {

    private final TokenRepository tokenRepository;

    @Test
    void save() {
        var token = domainBuilder.token().persist();
        assertThat(tokenRepository.findById(token.getTokenId())).get().isEqualTo(token);
    }

    @Test
    void nullCharacter() {
        var stringNullChar = "abc" + (char) 0;
        var token = domainBuilder.token().get();
        token.setName(stringNullChar);
        token.setSymbol(stringNullChar);
        tokenRepository.save(token);
        assertThat(tokenRepository.findById(token.getTokenId())).get().isEqualTo(token);
    }
}
