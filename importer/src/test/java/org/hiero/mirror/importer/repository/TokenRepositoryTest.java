// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.TokenRepository.Treasury;
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
    void findTreasuryByTokenId() {
        // given
        final var token = domainBuilder.token().persist();
        final var tokenWithoutKeys = domainBuilder
                .token()
                .customize(t -> t.freezeKey(null).kycKey(null))
                .persist();

        // when, then
        assertThat(tokenRepository.findTreasuryByTokenId(token.getTokenId()))
                .usingRecursiveComparison()
                .isEqualTo(new Treasury(token.getTreasuryAccountId(), token.getFreezeKey(), token.getKycKey()));
        assertThat(tokenRepository.findTreasuryByTokenId(tokenWithoutKeys.getTokenId()))
                .usingRecursiveComparison()
                .isEqualTo(new Treasury(tokenWithoutKeys.getTreasuryAccountId(), null, null));
        assertThat(tokenRepository.findTreasuryByTokenId(domainBuilder.id())).isNull();
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
