// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EthereumTransactionRepositoryTest extends Web3IntegrationTest {
    private final EthereumTransactionRepository ethereumTransactionRepository;

    @Test
    void findByConsensusTimestampSuccessful() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(false).persist();
        assertThat(ethereumTransactionRepository.findById(ethereumTransaction.getConsensusTimestamp()))
                .contains(ethereumTransaction);
    }

    @Test
    void findByConsensusTimestampAndPayerAccountIdSuccessful() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(false).persist();
        assertThat(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        ethereumTransaction.getConsensusTimestamp(), ethereumTransaction.getPayerAccountId()))
                .contains(ethereumTransaction);
    }
}
