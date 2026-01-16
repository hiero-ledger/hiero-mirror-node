// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.consensus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicUpdateTransaction;
import java.time.Duration;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class ConsensusUpdateTopicTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        ConsensusUpdateTopicTransactionSupplier consensusUpdateTopicTransactionSupplier =
                new ConsensusUpdateTopicTransactionSupplier();
        consensusUpdateTopicTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicUpdateTransaction actual = consensusUpdateTopicTransactionSupplier.get();

        assertThat(actual)
                .returns(null, TopicUpdateTransaction::getAdminKey)
                .returns(null, TopicUpdateTransaction::getAutoRenewAccountId)
                .returns(null, TopicUpdateTransaction::getAutoRenewPeriod)
                .returns(MAX_TRANSACTION_FEE_HBAR, TopicUpdateTransaction::getMaxTransactionFee)
                .returns(null, TopicUpdateTransaction::getSubmitKey)
                .returns(TOPIC_ID, TopicUpdateTransaction::getTopicId)
                .extracting(TopicUpdateTransaction::getTopicMemo, STRING)
                .contains("Mirror node updated test topic");
    }

    @Test
    void createWithCustomData() {
        PublicKey key = PrivateKey.generateED25519().getPublicKey();
        Duration autoRenewPeriod = Duration.ofSeconds(1);

        ConsensusUpdateTopicTransactionSupplier consensusUpdateTopicTransactionSupplier =
                new ConsensusUpdateTopicTransactionSupplier();
        consensusUpdateTopicTransactionSupplier.setAdminKey(key.toString());
        consensusUpdateTopicTransactionSupplier.setAutoRenewAccountId("0.0.2");
        consensusUpdateTopicTransactionSupplier.setAutoRenewPeriod(autoRenewPeriod);
        consensusUpdateTopicTransactionSupplier.setMaxTransactionFee(1);
        consensusUpdateTopicTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicUpdateTransaction actual = consensusUpdateTopicTransactionSupplier.get();

        assertThat(actual)
                .returns(key, TopicUpdateTransaction::getAdminKey)
                .returns(AccountId.fromString("0.0.2"), TopicUpdateTransaction::getAutoRenewAccountId)
                .returns(autoRenewPeriod, TopicUpdateTransaction::getAutoRenewPeriod)
                .returns(ONE_TINYBAR, TopicUpdateTransaction::getMaxTransactionFee)
                .returns(key, TopicUpdateTransaction::getSubmitKey)
                .returns(TOPIC_ID, TopicUpdateTransaction::getTopicId)
                .extracting(TopicUpdateTransaction::getTopicMemo, STRING)
                .contains("Mirror node updated test topic");
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return ConsensusUpdateTopicTransactionSupplier.class;
    }
}
