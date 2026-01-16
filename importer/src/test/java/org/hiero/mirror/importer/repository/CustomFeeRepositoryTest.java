// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
class CustomFeeRepositoryTest extends ImporterIntegrationTest {

    private final CustomFeeRepository customFeeRepository;
    private static final RowMapper<CustomFee> ROW_MAPPER = rowMapper(CustomFee.class);

    @Test
    void save() {
        var customFee = domainBuilder.customFee().get();
        customFeeRepository.save(customFee);
        assertThat(customFeeRepository.findById(customFee.getEntityId())).get().isEqualTo(customFee);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        var customFee = domainBuilder.customFee().persist();

        jdbcOperations.update("insert into custom_fee_history select * from custom_fee");
        List<CustomFee> customFeeHistory = jdbcOperations.query("select * from custom_fee_history", ROW_MAPPER);

        assertThat(customFeeRepository.findAll()).containsExactly(customFee);
        assertThat(customFeeHistory).containsExactly(customFee);
    }
}
