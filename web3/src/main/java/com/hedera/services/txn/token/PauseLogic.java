// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.web3.evm.store.Store;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Used token.changePauseStatus (like in services)
 * 3. Used store.updateToken(token)
 *    instead of tokenStore.commitToken(token) (like in services)
 */
public class PauseLogic {

    public void pause(final Id targetTokenId, final Store store) {
        /* --- Load the model objects --- */
        var token = store.loadPossiblyPausedToken(targetTokenId.asEvmAddress());

        /* --- Do the business logic --- */
        var pausedToken = token.changePauseStatus(true);

        /* --- Persist the updated models --- */
        store.updateToken(pausedToken);
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txnBody) {
        TokenPauseTransactionBody op = txnBody.getTokenPause();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return OK;
    }
}
