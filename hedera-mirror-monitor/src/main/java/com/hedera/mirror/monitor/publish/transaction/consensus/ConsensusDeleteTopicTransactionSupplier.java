// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.transaction.consensus;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TopicDeleteTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConsensusDeleteTopicTransactionSupplier implements TransactionSupplier<TopicDeleteTransaction> {

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank
    private String topicId;

    @Override
    public TopicDeleteTransaction get() {

        return new TopicDeleteTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTopicId(TopicId.fromString(topicId));
    }
}
