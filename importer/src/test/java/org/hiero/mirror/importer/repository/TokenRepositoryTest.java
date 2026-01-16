// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
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
