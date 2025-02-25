// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Used tokenRelationship.changeFrozenState (like in services)
 * 3. Used store.updateTokenRelationship(tokenRelationship)
 *    instead of tokenStore.commitTokenRelationships(List.of(tokenRelationship)) (like in services)
 */
public class FreezeLogic {

    public void freeze(final Id targetTokenId, final Id targetAccountId, final Store store) {
        /* --- Load the model objects --- */
        final var tokenRelationshipKey =
                new TokenRelationshipKey(targetTokenId.asEvmAddress(), targetAccountId.asEvmAddress());
        var tokenRelationship = store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW);

        /* --- Do the business logic --- */
        var frozenTokenRelationship = tokenRelationship.changeFrozenState(true);

        /* --- Persist the updated models --- */
        store.updateTokenRelationship(frozenTokenRelationship);
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        TokenFreezeAccountTransactionBody op = txnBody.getTokenFreeze();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        if (!op.hasAccount()) {
            return INVALID_ACCOUNT_ID;
        }

        return OK;
    }
}
