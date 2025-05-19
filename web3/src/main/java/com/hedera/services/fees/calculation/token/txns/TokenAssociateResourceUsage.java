// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation.token.txns;

import static com.hedera.services.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.usage.token.TokenAssociateUsage;
import com.hedera.services.hapi.fees.usage.EstimatorFactory;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.services.hapi.utils.fees.SigValueObj;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.BiFunction;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;

public class TokenAssociateResourceUsage extends AbstractTokenResourceUsage implements TxnResourceUsageEstimator {

    private static final BiFunction<TransactionBody, TxnUsageEstimator, TokenAssociateUsage> factory =
            TokenAssociateUsage::newEstimate;

    private final Store store;

    public TokenAssociateResourceUsage(EstimatorFactory estimatorFactory, Store store) {
        super(estimatorFactory);
        this.store = store;
    }

    @Override
    public boolean applicableTo(TransactionBody txn) {
        return txn.hasTokenAssociate();
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj svo) {
        final var op = txn.getTokenAssociate();
        final var account = store.getAccount(EntityIdUtils.asTypedEvmAddress(op.getAccount()), OnMissing.DONT_THROW);

        if (account == null) {
            return FeeData.getDefaultInstance();
        } else {
            final var sigUsage =
                    new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
            final var estimate = factory.apply(txn, estimatorFactory.get(sigUsage, txn, ESTIMATOR_UTILS));
            return estimate.givenCurrentExpiry(account.getExpiry()).get();
        }
    }
}
