// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.common.domain.DomainBuilder;
import org.junit.jupiter.api.Test;

class EthereumTransactionTest {

    @Test
    void toTransactionHash() {
        var domainBuilder = new DomainBuilder();
        var ethereumTransaction = domainBuilder.ethereumTransaction(true).get();
        var expected = new TransactionHash();
        expected.setConsensusTimestamp(ethereumTransaction.getConsensusTimestamp());
        expected.setHash(ethereumTransaction.getHash());
        expected.setPayerAccountId(ethereumTransaction.getPayerAccountId().getId());
        assertThat(ethereumTransaction.toTransactionHash()).isEqualTo(expected);
    }
}
