// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.consensus;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TopicDeleteTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class ConsensusDeleteTopicTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ConsensusDeleteTopicTransactionSupplier consensusDeleteTopicTransactionSupplier =
                new ConsensusDeleteTopicTransactionSupplier();
        consensusDeleteTopicTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicDeleteTransaction actual = consensusDeleteTopicTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TopicDeleteTransaction::getMaxTransactionFee)
                .returns(TOPIC_ID, TopicDeleteTransaction::getTopicId);
    }

    @Test
    void createWithCustomData() {
        ConsensusDeleteTopicTransactionSupplier consensusDeleteTopicTransactionSupplier =
                new ConsensusDeleteTopicTransactionSupplier();
        consensusDeleteTopicTransactionSupplier.setMaxTransactionFee(1);
        consensusDeleteTopicTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicDeleteTransaction actual = consensusDeleteTopicTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, TopicDeleteTransaction::getMaxTransactionFee)
                .returns(TOPIC_ID, TopicDeleteTransaction::getTopicId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return ConsensusDeleteTopicTransactionSupplier.class;
    }
}
