// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.Resource;
import java.util.List;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

class TokenAccountRepositoryTest extends ImporterIntegrationTest {

    private static final RowMapper<TokenAccount> ROW_MAPPER = rowMapper(TokenAccount.class);

    @Resource
    protected TokenAccountRepository tokenAccountRepository;

    @Test
    void save() {
        var tokenAccount = domainBuilder.tokenAccount().get();
        tokenAccountRepository.save(tokenAccount);
        assertThat(tokenAccountRepository.findById(tokenAccount.getId())).get().isEqualTo(tokenAccount);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        var tokenAccount = domainBuilder.tokenAccount().persist();

        jdbcOperations.update("insert into token_account_history select * from token_account");
        List<TokenAccount> tokenAccountHistory =
                jdbcOperations.query("select * from token_account_history", ROW_MAPPER);

        assertThat(tokenAccountRepository.findAll()).containsExactly(tokenAccount);
        assertThat(tokenAccountHistory).containsExactly(tokenAccount);
    }
}
