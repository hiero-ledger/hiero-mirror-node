// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
class NftAllowanceRepositoryTest extends ImporterIntegrationTest {

    private static final RowMapper<NftAllowance> ROW_MAPPER = rowMapper(NftAllowance.class);

    private final NftAllowanceRepository nftAllowanceRepository;

    @Test
    void save() {
        NftAllowance nftAllowance = domainBuilder.nftAllowance().persist();
        assertThat(nftAllowanceRepository.findById(nftAllowance.getId())).get().isEqualTo(nftAllowance);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        NftAllowance nftAllowance = domainBuilder.nftAllowance().persist();

        jdbcOperations.update("insert into nft_allowance_history select * from nft_allowance");
        List<NftAllowance> nftAllowanceHistory =
                jdbcOperations.query("select * from nft_allowance_history", ROW_MAPPER);

        assertThat(nftAllowanceRepository.findAll()).containsExactly(nftAllowance);
        assertThat(nftAllowanceHistory).containsExactly(nftAllowance);
    }
}
