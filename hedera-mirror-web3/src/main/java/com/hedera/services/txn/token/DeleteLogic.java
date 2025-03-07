// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Removed SigImpactHistorian
 */
public class DeleteLogic {
    public void delete(TokenID grpcTokenId, final Store store) {
        // --- Convert to model id ---
        final var targetTokenId = EntityIdUtils.asTypedEvmAddress(grpcTokenId);
        // --- Load the model object ---
        final var token = store.getToken(targetTokenId, Store.OnMissing.THROW);

        // --- Do the business logic ---
        final var deletedToken = token.delete();

        // --- Persist the updated model ---
        store.updateToken(deletedToken);
    }

    public ResponseCodeEnum validate(final TransactionBody txnBody) {
        final TokenDeleteTransactionBody op = txnBody.getTokenDeletion();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return OK;
    }
}
