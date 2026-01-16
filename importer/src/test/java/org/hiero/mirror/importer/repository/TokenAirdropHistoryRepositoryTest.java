// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenAirdropHistoryRepositoryTest extends ImporterIntegrationTest {

    private final TokenAirdropHistoryRepository tokenAirdropHistoryRepository;

    @Test
    void prune() {
        domainBuilder.tokenAirdropHistory(TokenTypeEnum.NON_FUNGIBLE_UNIQUE).persist();
        var tokenAirdropHistory2 =
                domainBuilder.tokenAirdropHistory(TokenTypeEnum.FUNGIBLE_COMMON).persist();
        var tokenAirdropHistory3 = domainBuilder
                .tokenAirdropHistory(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .persist();

        tokenAirdropHistoryRepository.prune(tokenAirdropHistory2.getTimestampUpper());

        assertThat(tokenAirdropHistoryRepository.findAll()).containsExactly(tokenAirdropHistory3);
    }

    @Test
    void save() {
        var tokenAirdropHistory = domainBuilder
                .tokenAirdropHistory(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .get();
        tokenAirdropHistoryRepository.save(tokenAirdropHistory);
        assertThat(tokenAirdropHistoryRepository.findAll()).containsOnly(tokenAirdropHistory);
    }
}
