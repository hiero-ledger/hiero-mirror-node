// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.utils.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CryptoFeeBuilderTest {
    private CryptoFeeBuilder subject;

    @BeforeEach
    void setUp() {
        subject = new CryptoFeeBuilder();
    }

    @Test
    void getsCorrectTransactionRecordQueryFeeMatrices() {
        assertEquals(
                FeeData.getDefaultInstance(),
                subject.getTransactionRecordQueryFeeMatrices(null, ResponseType.COST_ANSWER));

        final var transRecord = txRecordBuilder().build();
        var feeData = subject.getTransactionRecordQueryFeeMatrices(transRecord, ResponseType.COST_ANSWER);
        assertQueryFee(feeData, 148L);

        feeData = subject.getTransactionRecordQueryFeeMatrices(transRecord, ResponseType.ANSWER_STATE_PROOF);
        assertQueryFee(feeData, 2148L);

        feeData = subject.getTransactionRecordQueryFeeMatrices(recordWithMemo(), ResponseType.ANSWER_ONLY);
        assertQueryFee(feeData, 158L);

        feeData = subject.getTransactionRecordQueryFeeMatrices(
                recordWithTransferList(), ResponseType.COST_ANSWER_STATE_PROOF);
        assertQueryFee(feeData, 2500L);
    }

    private void assertQueryFee(final FeeData feeData, final long expectedBpr) {
        final var expectedBpt = FeeBuilder.BASIC_QUERY_HEADER + FeeBuilder.BASIC_TX_ID_SIZE;
        final var nodeFee = feeBuilder().setBpt(expectedBpt).setBpr(expectedBpr).build();

        assertEquals(FeeComponents.getDefaultInstance(), feeData.getServicedata());
        assertEquals(FeeComponents.getDefaultInstance(), feeData.getNetworkdata());
        assertEquals(nodeFee, feeData.getNodedata());
    }

    private TransactionRecord recordWithMemo() {
        return txRecordBuilder().setMemo("0123456789").build();
    }

    private TransactionRecord recordWithTransferList() {
        final var transferList = TransferList.newBuilder();
        for (int i = -5; i <= 5; i++) {
            transferList.addAccountAmounts(AccountAmount.newBuilder().setAmount(i));
        }
        return txRecordBuilder().setTransferList(transferList).build();
    }

    private TransactionRecord.Builder txRecordBuilder() {
        return TransactionRecord.newBuilder();
    }

    private FeeComponents.Builder feeBuilder() {
        return FeeComponents.newBuilder().setConstant(FeeBuilder.FEE_MATRICES_CONST);
    }
}
