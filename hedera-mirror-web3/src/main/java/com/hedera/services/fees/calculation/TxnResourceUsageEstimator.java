// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.hapi.utils.fees.SigValueObj;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 */
public interface TxnResourceUsageEstimator {

    /**
     * Flags whether the estimator applies to the given transaction.
     *
     * @param txn the txn in question
     * @return if the estimator applies
     */
    boolean applicableTo(TransactionBody txn);

    /**
     * Returns the estimated resource usage for the given txn relative to the given state of the
     * world.
     *
     * @param txn      the txn in question
     * @param sigUsage the signature usage
     * @return the estimated resource usage
     * @throws NullPointerException or analogous if the estimator does not apply to the txn
     */
    @SuppressWarnings("java:S112")
    FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage);
}
