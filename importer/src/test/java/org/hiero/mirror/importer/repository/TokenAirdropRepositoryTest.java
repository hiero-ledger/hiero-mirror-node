// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenAirdropRepositoryTest extends ImporterIntegrationTest {

    private final TokenAirdropRepository repository;

    @Test
    void saveFungible() {
        var tokenAirdrop =
                domainBuilder.tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON).get();
        repository.save(tokenAirdrop);
        assertThat(repository.findAll()).containsOnly(tokenAirdrop);
    }

    @Test
    void saveNft() {
        var tokenAirdrop =
                domainBuilder.tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE).get();
        repository.save(tokenAirdrop);
        assertThat(repository.findAll()).containsOnly(tokenAirdrop);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        var tokenAirdrop =
                domainBuilder.tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON).persist();

        jdbcOperations.update("insert into token_airdrop_history select * from token_airdrop");
        var tokenAirdropHistory = findHistory(TokenAirdrop.class);

        assertThat(repository.findAll()).containsExactly(tokenAirdrop);
        assertThat(tokenAirdropHistory).containsExactly(tokenAirdrop);
    }
}
