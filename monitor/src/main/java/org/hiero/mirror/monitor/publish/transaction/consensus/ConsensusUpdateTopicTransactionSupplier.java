// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.consensus;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicUpdateTransaction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.monitor.publish.transaction.AdminKeyable;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.hiero.mirror.monitor.util.Utility;

@Data
public class ConsensusUpdateTopicTransactionSupplier
        implements TransactionSupplier<TopicUpdateTransaction>, AdminKeyable {

    private String adminKey;

    private String autoRenewAccountId;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration autoRenewPeriod = Duration.ofSeconds(8000000);

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank
    private String topicId;

    @Override
    public TopicUpdateTransaction get() {
        TopicUpdateTransaction topicUpdateTransaction = new TopicUpdateTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTopicId(TopicId.fromString(topicId))
                .setTopicMemo(Utility.getMemo("Mirror node updated test topic"));

        if (adminKey != null) {
            PublicKey key = PublicKey.fromString(adminKey);
            topicUpdateTransaction.setAdminKey(key).setSubmitKey(key);
        }
        if (autoRenewAccountId != null) {
            topicUpdateTransaction
                    .setAutoRenewAccountId(AccountId.fromString(autoRenewAccountId))
                    .setAutoRenewPeriod(autoRenewPeriod);
        }
        return topicUpdateTransaction;
    }
}
