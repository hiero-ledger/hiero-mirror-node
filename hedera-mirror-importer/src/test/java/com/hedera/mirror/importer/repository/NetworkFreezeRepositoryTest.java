// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class NetworkFreezeRepositoryTest extends ImporterIntegrationTest {

    private final NetworkFreezeRepository networkFreezeRepository;

    @Test
    void prune() {
        domainBuilder.networkFreeze().persist();
        var networkFreeze2 = domainBuilder.networkFreeze().persist();
        var networkFreeze3 = domainBuilder.networkFreeze().persist();

        networkFreezeRepository.prune(networkFreeze2.getConsensusTimestamp());

        assertThat(networkFreezeRepository.findAll()).containsExactlyInAnyOrder(networkFreeze3);
    }

    @Test
    void save() {
        var networkFreeze = domainBuilder.networkFreeze().get();
        networkFreezeRepository.save(networkFreeze);
        assertThat(networkFreezeRepository.findById(networkFreeze.getConsensusTimestamp()))
                .get()
                .isEqualTo(networkFreeze);
    }
}
