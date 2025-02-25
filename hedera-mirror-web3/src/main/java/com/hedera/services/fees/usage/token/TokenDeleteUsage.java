// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.usage.token;

import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Exact copy from hedera-services
 */
public class TokenDeleteUsage extends TokenTxnUsage<TokenDeleteUsage> {
    private TokenDeleteUsage(TransactionBody tokenDeletionOp, TxnUsageEstimator usageEstimator) {
        super(tokenDeletionOp, usageEstimator);
    }

    public static TokenDeleteUsage newEstimate(TransactionBody tokenDeletionOp, TxnUsageEstimator usageEstimator) {
        return new TokenDeleteUsage(tokenDeletionOp, usageEstimator);
    }

    @Override
    TokenDeleteUsage self() {
        return this;
    }

    public FeeData get() {
        addEntityBpt();
        return usageEstimator.get();
    }
}
