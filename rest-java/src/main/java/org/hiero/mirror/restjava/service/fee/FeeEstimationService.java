// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.node.app.fees.StandaloneFeeCalculator;
import com.hedera.pbj.runtime.ParseException;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.hapi.fees.FeeResult;
import org.hiero.mirror.rest.model.FeeEstimateMode;

@Named
@RequiredArgsConstructor
public final class FeeEstimationService {

    private final StandaloneFeeCalculator calculator;

    public FeeResult estimateFees(Transaction transaction, FeeEstimateMode mode) {
        if (mode == FeeEstimateMode.STATE) {
            throw new IllegalArgumentException("State-based fee estimation is not supported");
        }
        try {
            Transaction pbjTxn;
            if (transaction.signedTransactionBytes().length() > 0) {
                pbjTxn = transaction;
            } else if (transaction.bodyBytes().length() > 0) {
                final var signedTxn = SignedTransaction.newBuilder()
                        .bodyBytes(transaction.bodyBytes())
                        .build();
                pbjTxn = Transaction.newBuilder()
                        .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(signedTxn))
                        .build();
            } else {
                throw new IllegalArgumentException("Transaction must contain body bytes or signed transaction bytes");
            }
            return calculator.calculateIntrinsic(pbjTxn);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Unable to parse transaction", e);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Unknown transaction type", e);
        }
    }
}
