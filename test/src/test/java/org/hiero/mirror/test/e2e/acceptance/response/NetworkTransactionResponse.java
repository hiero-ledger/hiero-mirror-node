// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.response;

import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Value
public class NetworkTransactionResponse {

    private final TransactionId transactionId;
    private final TransactionReceipt receipt;

    // interim function until mirror node supports checksum
    public String getTransactionIdStringNoCheckSum() {
        String accountIdString = transactionId.accountId.toString().split("-")[0];
        return accountIdString + "-" + transactionId.validStart.getEpochSecond() + "-" + getPaddedNanos();
    }

    public String getValidStartString() {

        // left pad nanos with zeros where applicable
        return transactionId.validStart.getEpochSecond() + "." + getPaddedNanos();
    }

    private String getPaddedNanos() {
        String nanos = String.valueOf(transactionId.validStart.getNano());
        return StringUtils.leftPad(nanos, 9, '0');
    }
}
