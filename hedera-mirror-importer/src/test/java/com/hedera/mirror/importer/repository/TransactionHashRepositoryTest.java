// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
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
