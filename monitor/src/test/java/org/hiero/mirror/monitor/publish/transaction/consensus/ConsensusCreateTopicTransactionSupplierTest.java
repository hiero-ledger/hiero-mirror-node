// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.consensus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class ConsensusCreateTopicTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ConsensusCreateTopicTransactionSupplier consensusCreateTopicTransactionSupplier =
                new ConsensusCreateTopicTransactionSupplier();
        TopicCreateTransaction actual = consensusCreateTopicTransactionSupplier.get();

        assertThat(actual)
                .returns(null, TopicCreateTransaction::getAdminKey)
                .returns(null, TopicCreateTransaction::getAutoRenewAccountId)
                .returns(MAX_TRANSACTION_FEE_HBAR, TopicCreateTransaction::getMaxTransactionFee)
                .returns(null, TopicCreateTransaction::getSubmitKey)
                .extracting(TopicCreateTransaction::getTopicMemo, STRING)
                .contains("Mirror node created test topic");
    }

    @Test
    void createWithCustomData() {
        PublicKey key = PrivateKey.generateED25519().getPublicKey();

        ConsensusCreateTopicTransactionSupplier consensusCreateTopicTransactionSupplier =
                new ConsensusCreateTopicTransactionSupplier();
        consensusCreateTopicTransactionSupplier.setAdminKey(key.toString());
        consensusCreateTopicTransactionSupplier.setAutoRenewAccountId(ACCOUNT_ID.toString());
        consensusCreateTopicTransactionSupplier.setMaxTransactionFee(1);
        TopicCreateTransaction actual = consensusCreateTopicTransactionSupplier.get();

        assertThat(actual)
                .returns(key, TopicCreateTransaction::getAdminKey)
                .returns(ACCOUNT_ID, TopicCreateTransaction::getAutoRenewAccountId)
                .returns(ONE_TINYBAR, TopicCreateTransaction::getMaxTransactionFee)
                .returns(key, TopicCreateTransaction::getSubmitKey)
                .extracting(TopicCreateTransaction::getTopicMemo, STRING)
                .contains("Mirror node created test topic");
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return ConsensusCreateTopicTransactionSupplier.class;
    }
}
