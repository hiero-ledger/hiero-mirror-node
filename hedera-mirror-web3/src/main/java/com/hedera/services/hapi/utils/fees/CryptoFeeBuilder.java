// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.utils.fees;

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionRecord;

/**
 * This class includes methods for generating Fee Matrices and calculating Fee for Crypto related
 * Transactions and Query.
 *
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Removed unused methods
 */
public final class CryptoFeeBuilder extends FeeBuilder {

    public CryptoFeeBuilder() {
        super();
    }

    /**
     * This method returns the fee matrices for transaction record query
     *
     * @param transRecord transaction record
     * @param responseType response type
     * @return fee data
     */
    public FeeData getTransactionRecordQueryFeeMatrices(
            final TransactionRecord transRecord, final ResponseType responseType) {
        if (transRecord == null) {
            return FeeData.getDefaultInstance();
        }
        final var bpt = BASIC_QUERY_HEADER + BASIC_TX_ID_SIZE;
        final var txRecordSize = getAccountTransactionRecordSize(transRecord);
        final var bpr = BASIC_QUERY_RES_HEADER + txRecordSize + getStateProofSize(responseType);

        final var feeMatrices = FeeComponents.newBuilder()
                .setBpt(bpt)
                .setVpt(0L)
                .setRbh(0L)
                .setSbh(0L)
                .setGas(0L)
                .setTv(0L)
                .setBpr(bpr)
                .setSbpr(0L)
                .build();

        return getQueryFeeDataMatrices(feeMatrices);
    }

    private int getAccountTransactionRecordSize(final TransactionRecord transRecord) {
        final var memoBytesSize = transRecord.getMemoBytes().size();

        final var accountAmountSize = transRecord.hasTransferList()
                ? transRecord.getTransferList().getAccountAmountsCount() * BASIC_ACCOUNT_AMT_SIZE
                : 0;

        return BASIC_TX_RECORD_SIZE + memoBytesSize + accountAmountSize;
    }
}
