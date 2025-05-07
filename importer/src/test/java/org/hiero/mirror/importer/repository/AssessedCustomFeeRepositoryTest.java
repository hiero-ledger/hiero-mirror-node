// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
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
