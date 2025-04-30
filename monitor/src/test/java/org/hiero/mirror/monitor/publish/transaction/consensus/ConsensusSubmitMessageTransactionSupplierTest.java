// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.consensus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import org.hiero.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class ConsensusSubmitMessageTransactionSupplierTest extends AbstractTransactionSupplierTest {

    private static final Hbar MAX_TRANSACTION_FEE_HBAR = Hbar.fromTinybars(1_000_000);

    @Test
    void createWithMinimumData() {
        ConsensusSubmitMessageTransactionSupplier consensusSubmitMessageTransactionSupplier =
                new ConsensusSubmitMessageTransactionSupplier();
        consensusSubmitMessageTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicMessageSubmitTransaction actual = consensusSubmitMessageTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TopicMessageSubmitTransaction::getMaxTransactionFee)
                .returns(TOPIC_ID, TopicMessageSubmitTransaction::getTopicId)
                .extracting(a -> a.getMessage().toStringUtf8(), STRING)
                .hasSize(256);
    }

    @Test
    void createWithCustomMessageSize() {
        ConsensusSubmitMessageTransactionSupplier consensusSubmitMessageTransactionSupplier =
                new ConsensusSubmitMessageTransactionSupplier();
        consensusSubmitMessageTransactionSupplier.setMaxTransactionFee(1);
        consensusSubmitMessageTransactionSupplier.setMessageSize(14);
        consensusSubmitMessageTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicMessageSubmitTransaction actual = consensusSubmitMessageTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, TopicMessageSubmitTransaction::getMaxTransactionFee)
                .returns(TOPIC_ID, TopicMessageSubmitTransaction::getTopicId)
                .satisfies(a -> assertThat(a.getMessage()).isNotNull())
                .extracting(a -> a.getMessage().toStringUtf8(), STRING)
                .hasSize(14);
    }

    @Test
    void createWithCustomMessage() {
        String message = "ConsensusSubmitMessageTransactionSupplierTest.createWithCustomData";
        ConsensusSubmitMessageTransactionSupplier consensusSubmitMessageTransactionSupplier =
                new ConsensusSubmitMessageTransactionSupplier();
        consensusSubmitMessageTransactionSupplier.setMaxTransactionFee(1);
        consensusSubmitMessageTransactionSupplier.setMessage(message);
        consensusSubmitMessageTransactionSupplier.setTopicId(TOPIC_ID.toString());
        TopicMessageSubmitTransaction actual = consensusSubmitMessageTransactionSupplier.get();

        assertThat(actual)
                .returns(ByteString.copyFromUtf8(message), TopicMessageSubmitTransaction::getMessage)
                .returns(ONE_TINYBAR, TopicMessageSubmitTransaction::getMaxTransactionFee)
                .returns(TOPIC_ID, TopicMessageSubmitTransaction::getTopicId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return ConsensusSubmitMessageTransactionSupplier.class;
    }
}
