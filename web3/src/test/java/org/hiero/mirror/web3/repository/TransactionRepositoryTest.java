// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.transaction.Transaction;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TransactionRepositoryTest extends Web3IntegrationTest {
    private final TransactionRepository transactionRepository;

    @Test
    void findByConsensusTimestampSuccessful() {
        Transaction transaction = domainBuilder.transaction().persist();
        assertThat(transactionRepository.findById(transaction.getConsensusTimestamp()))
                .contains(transaction);
    }

    @Test
    void findByPayerAccountIdAndValidStartNsSuccessful() {
        var transaction = domainBuilder.transaction().persist();
        assertThat(transactionRepository.findByPayerAccountIdAndValidStartNs(
                        transaction.getPayerAccountId(), transaction.getValidStartNs()))
                .contains(transaction);
    }
}
