// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.Resource;
import java.util.List;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

class TokenAllowanceRepositoryTest extends ImporterIntegrationTest {

    private static final RowMapper<TokenAllowance> ROW_MAPPER = rowMapper(TokenAllowance.class);

    @Resource
    private TokenAllowanceRepository tokenAllowanceRepository;

    @Test
    void save() {
        TokenAllowance tokenAllowance = domainBuilder.tokenAllowance().persist();
        assertThat(tokenAllowanceRepository.findById(tokenAllowance.getId()))
                .get()
                .isEqualTo(tokenAllowance);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        TokenAllowance tokenAllowance = domainBuilder.tokenAllowance().persist();

        jdbcOperations.update("insert into token_allowance_history select * from token_allowance");
        List<TokenAllowance> tokenAllowanceHistory =
                jdbcOperations.query("select * from token_allowance_history", ROW_MAPPER);

        assertThat(tokenAllowanceRepository.findAll()).containsExactly(tokenAllowance);
        assertThat(tokenAllowanceHistory).containsExactly(tokenAllowance);
    }
}
