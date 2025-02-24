// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Used token.changePauseStatus (like in services)
 * 3. Used store.updateToken(token) instead of tokenStore.commitToken(token) (like in services)
 */
public class UnpauseLogic {

    public void unpause(final Id targetTokenId, final Store store) {
        /* --- Load the model objects --- */
        var token = store.loadPossiblyPausedToken(targetTokenId.asEvmAddress());

        /* --- Do the business logic --- */
        var unpausedToken = token.changePauseStatus(false);

        /* --- Persist the updated models --- */
        store.updateToken(unpausedToken);
    }

    public ResponseCodeEnum validateSyntax(TransactionBody txnBody) {
        TokenUnpauseTransactionBody op = txnBody.getTokenUnpause();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return OK;
    }
}
