// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.response;

import com.google.common.base.Stopwatch;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.rest.model.TransactionByIdResponse;
import org.hiero.mirror.test.e2e.acceptance.util.TestUtil;
import org.jspecify.annotations.Nullable;

@Value
public class NetworkTransactionResponse {

    private final Stopwatch stopwatch;
    private final TransactionId transactionId;
    private final TransactionReceipt receipt;

    /** Transactions already loaded from the mirror node, when fee verification fetched them. */
    @Nullable
    private final TransactionByIdResponse transactions;

    // interim function until mirror node supports checksum
    public String getTransactionIdStringNoCheckSum() {
        return TestUtil.toRestTransactionId(transactionId);
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
