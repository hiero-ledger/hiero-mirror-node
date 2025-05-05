// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction.consensus;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.hiero.mirror.monitor.util.Utility;

@Data
public class ConsensusSubmitMessageTransactionSupplier implements TransactionSupplier<TopicMessageSubmitTransaction> {

    @Min(1)
    private long maxTransactionFee = 1_000_000;

    @NotNull
    private String message = StringUtils.EMPTY;

    @Min(14)
    @Max(6144)
    private int messageSize = 256;

    @NotBlank
    private String topicId;

    // Internal variables that are cached for performance reasons
    @Getter(lazy = true)
    private final TopicId consensusTopicId = TopicId.fromString(topicId);

    @Override
    public TopicMessageSubmitTransaction get() {
        return new TopicMessageSubmitTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setMessage(
                        !message.isEmpty()
                                ? message.getBytes(StandardCharsets.UTF_8)
                                : Utility.generateMessage(messageSize))
                .setTopicId(getConsensusTopicId());
    }
}
