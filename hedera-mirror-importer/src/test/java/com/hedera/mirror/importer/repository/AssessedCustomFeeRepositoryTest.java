// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class AssessedCustomFeeRepositoryTest extends ImporterIntegrationTest {

    private final AssessedCustomFeeRepository assessedCustomFeeRepository;

    @Test
    void prune() {
        domainBuilder.assessedCustomFee().persist();
        var assessedCustomFee2 = domainBuilder.assessedCustomFee().persist();
        var assessedCustomFee3 = domainBuilder.assessedCustomFee().persist();

        assessedCustomFeeRepository.prune(assessedCustomFee2.getId().getConsensusTimestamp());

        assertThat(assessedCustomFeeRepository.findAll()).containsExactly(assessedCustomFee3);
    }

    @Test
    void save() {
        var assessedCustomFee = domainBuilder.assessedCustomFee().get();
        assessedCustomFeeRepository.save(assessedCustomFee);
        assertThat(assessedCustomFeeRepository.findById(assessedCustomFee.getId()))
                .get()
                .isEqualTo(assessedCustomFee);
    }
}
