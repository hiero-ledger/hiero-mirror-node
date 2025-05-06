// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TransactionHashRepositoryTest extends ImporterIntegrationTest {

    private final TransactionHashRepository transactionHashRepository;

    @Test
    void save() {
        var transactionHash = domainBuilder.transactionHash().get();
        transactionHashRepository.save(transactionHash);
        assertThat(transactionHashRepository.findById(transactionHash.getHash()))
                .contains(transactionHash);
    }
}
