// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.Resource;
import java.util.List;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

class CryptoAllowanceRepositoryTest extends ImporterIntegrationTest {

    private static final RowMapper<CryptoAllowance> ROW_MAPPER = rowMapper(CryptoAllowance.class);

    @Resource
    private CryptoAllowanceRepository cryptoAllowanceRepository;

    @Test
    void save() {
        CryptoAllowance cryptoAllowance = domainBuilder.cryptoAllowance().persist();
        assertThat(cryptoAllowanceRepository.findById(cryptoAllowance.getId()))
                .get()
                .isEqualTo(cryptoAllowance);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        CryptoAllowance cryptoAllowance = domainBuilder.cryptoAllowance().persist();

        jdbcOperations.update("insert into crypto_allowance_history select * from crypto_allowance");
        List<CryptoAllowance> cryptoAllowanceHistory =
                jdbcOperations.query("select * from crypto_allowance_history", ROW_MAPPER);

        assertThat(cryptoAllowanceRepository.findAll()).containsExactly(cryptoAllowance);
        assertThat(cryptoAllowanceHistory).containsExactly(cryptoAllowance);
    }
}
