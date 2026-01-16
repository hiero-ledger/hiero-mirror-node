// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionRecord;
import java.time.Instant;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class PublishResponse {

    @ToString.Exclude
    private final PublishRequest request;

    private final TransactionRecord transactionRecord;
    private final TransactionReceipt receipt;
    private final Instant timestamp;
    private final TransactionId transactionId;
}
