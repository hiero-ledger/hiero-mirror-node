// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.usage.token;

import static com.hedera.services.fees.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;

import com.hedera.services.fees.usage.token.entities.TokenEntitySizes;
import com.hedera.services.hapi.fees.usage.TxnUsage;
import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 *  Exact copy from hedera-services
 */
public abstract class TokenTxnUsage<T extends TokenTxnUsage<T>> extends TxnUsage {
    static TokenEntitySizes tokenEntitySizes = TOKEN_ENTITY_SIZES;

    protected TokenTxnUsage(final TransactionBody tokenOp, final TxnUsageEstimator usageEstimator) {
        super(tokenOp, usageEstimator);
    }

    abstract T self();

    void addTokenTransfersRecordRb(final int numTokens, final int fungibleNumTransfers, final int uniqueNumTransfers) {
        addRecordRb(
                tokenEntitySizes.bytesUsedToRecordTokenTransfers(numTokens, fungibleNumTransfers, uniqueNumTransfers));
    }

    public T novelRelsLasting(final int n, final long secs) {
        usageEstimator.addRbs(n * tokenEntitySizes.bytesUsedPerAccountRelationship() * secs);
        return self();
    }
}
