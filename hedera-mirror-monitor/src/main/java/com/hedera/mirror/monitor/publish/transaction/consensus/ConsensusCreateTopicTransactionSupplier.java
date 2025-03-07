// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.transaction.consensus;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.mirror.monitor.publish.transaction.AdminKeyable;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.util.Utility;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ConsensusCreateTopicTransactionSupplier
        implements TransactionSupplier<TopicCreateTransaction>, AdminKeyable {

    private String adminKey;

    private String autoRenewAccountId;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Override
    public TopicCreateTransaction get() {
        TopicCreateTransaction topicCreateTransaction = new TopicCreateTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTopicMemo(Utility.getMemo("Mirror node created test topic"));

        if (adminKey != null) {
            PublicKey key = PublicKey.fromString(adminKey);
            topicCreateTransaction.setAdminKey(key).setSubmitKey(key);
        }
        if (autoRenewAccountId != null) {
            topicCreateTransaction.setAutoRenewAccountId(AccountId.fromString(autoRenewAccountId));
        }
        return topicCreateTransaction;
    }
}
