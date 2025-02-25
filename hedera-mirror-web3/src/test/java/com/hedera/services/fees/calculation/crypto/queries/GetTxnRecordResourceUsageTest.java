// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation.crypto.queries;

import static com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage.MISSING_RECORD_STANDIN;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.hapi.utils.fees.CryptoFeeBuilder;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTxnRecordResourceUsageTest {
    private CryptoFeeBuilder usageEstimator;
    private GetTxnRecordResourceUsage subject;

    private static final TransactionID targetTxnId = TransactionID.newBuilder()
            .setAccountID(asAccount("0.0.2"))
            .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
            .build();

    public static Query queryOf(final TransactionGetRecordQuery op) {
        return Query.newBuilder().setTransactionGetRecord(op).build();
    }

    private static final TransactionGetRecordQuery satisfiableAnswerOnly = txnRecordQuery(targetTxnId, ANSWER_ONLY);
    private static final Query satisfiableAnswerOnlyQuery = queryOf(satisfiableAnswerOnly);

    public static TransactionGetRecordQuery txnRecordQuery(final TransactionID txnId, final ResponseType type) {
        return txnRecordQuery(txnId, type, false);
    }

    public static TransactionGetRecordQuery txnRecordQuery(
            final TransactionID txnId, final ResponseType type, final boolean duplicates) {
        return txnRecordQuery(txnId, type, Transaction.getDefaultInstance(), duplicates);
    }

    public static TransactionGetRecordQuery txnRecordQuery(
            final TransactionID txnId,
            final ResponseType type,
            final Transaction paymentTxn,
            final boolean duplicates) {
        return txnRecordQuery(txnId, type, paymentTxn, duplicates, false);
    }

    public static TransactionGetRecordQuery txnRecordQuery(
            final TransactionID txnId,
            final ResponseType type,
            final Transaction paymentTxn,
            final boolean duplicates,
            final boolean children) {
        return TransactionGetRecordQuery.newBuilder()
                .setTransactionID(txnId)
                .setHeader(queryHeaderOf(type, paymentTxn))
                .setIncludeDuplicates(duplicates)
                .setIncludeChildRecords(children)
                .build();
    }

    public static QueryHeader.Builder queryHeaderOf(final ResponseType type, final Transaction paymentTxn) {
        return queryHeaderOf(type).setPayment(paymentTxn);
    }

    public static QueryHeader.Builder queryHeaderOf(final ResponseType type) {
        return QueryHeader.newBuilder().setResponseType(type);
    }

    @BeforeEach
    void setup() {
        usageEstimator = mock(CryptoFeeBuilder.class);
        subject = new GetTxnRecordResourceUsage(usageEstimator);
    }

    @Test
    void onlySetsPriorityRecordInQueryCxtIfFound() {
        final var answerOnlyUsage = mock(FeeData.class);
        final var queryCtx = new HashMap<String, Object>();
        given(usageEstimator.getTransactionRecordQueryFeeMatrices(MISSING_RECORD_STANDIN, ANSWER_ONLY))
                .willReturn(answerOnlyUsage);
        final var actual = subject.usageGiven(satisfiableAnswerOnlyQuery, queryCtx);
        assertSame(answerOnlyUsage, actual);
    }

    @Test
    void recognizesApplicableQueries() {
        assertTrue(subject.applicableTo(satisfiableAnswerOnlyQuery));
        assertFalse(subject.applicableTo(Query.getDefaultInstance()));
    }
}
