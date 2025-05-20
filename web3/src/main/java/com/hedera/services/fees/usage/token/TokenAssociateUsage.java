// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.usage.token;

import static com.hedera.services.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 *  Exact copy from hedera-services
 */
public class TokenAssociateUsage extends TokenTxnUsage<TokenAssociateUsage> {
    private long currentExpiry;

    private TokenAssociateUsage(TransactionBody tokenOp, TxnUsageEstimator usageEstimator) {
        super(tokenOp, usageEstimator);
    }

    public static TokenAssociateUsage newEstimate(TransactionBody tokenOp, TxnUsageEstimator usageEstimator) {
        return new TokenAssociateUsage(tokenOp, usageEstimator);
    }

    @Override
    TokenAssociateUsage self() {
        return this;
    }

    public TokenAssociateUsage givenCurrentExpiry(long expiry) {
        this.currentExpiry = expiry;
        return this;
    }

    public FeeData get() {
        var op = this.op.getTokenAssociate();
        addEntityBpt();
        op.getTokensList().forEach(t -> addEntityBpt());
        novelRelsLasting(op.getTokensCount(), ESTIMATOR_UTILS.relativeLifetime(this.op, currentExpiry));
        return usageEstimator.get();
    }
}
