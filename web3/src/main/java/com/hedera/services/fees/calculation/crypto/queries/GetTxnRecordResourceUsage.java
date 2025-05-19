// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation.crypto.queries;

import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.hapi.utils.fees.CryptoFeeBuilder;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * Copied Logic type from hedera-services. Differences with the original: 1. Removed child record logic 2. Removed
 * retching TransactionRecord from AnswerFunctions, since we do not save records in TxnIdRecentHistory
 */
public class GetTxnRecordResourceUsage implements QueryResourceUsageEstimator {
    static final TransactionRecord MISSING_RECORD_STANDIN = TransactionRecord.getDefaultInstance();
    private final CryptoFeeBuilder usageEstimator;

    public GetTxnRecordResourceUsage(CryptoFeeBuilder usageEstimator) {
        this.usageEstimator = usageEstimator;
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasTransactionGetRecord();
    }

    @Override
    public FeeData usageGiven(Query query, @Nullable Map<String, Object> queryCtx) {
        return usageFor(query.getTransactionGetRecord().getHeader().getResponseType());
    }

    // removed child records logic
    private FeeData usageFor(final ResponseType stateProofType) {
        return usageEstimator.getTransactionRecordQueryFeeMatrices(MISSING_RECORD_STANDIN, stateProofType);
    }
}
