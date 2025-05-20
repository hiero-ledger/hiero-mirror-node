// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees;

import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.jproto.JKey;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.*;
import org.hiero.mirror.web3.evm.store.Store;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 *  2. Remove unused methods: init, estimatedNonFeePayerAdjustments, estimateFee, computePayment, assessCryptoAutoRenewal
 */
public interface FeeCalculator {

    FeeObject estimatePayment(Query query, FeeData usagePrices, Timestamp at, ResponseType type);

    long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at);

    FeeObject computeFee(TxnAccessor accessor, JKey payerKey, Timestamp at);
}
