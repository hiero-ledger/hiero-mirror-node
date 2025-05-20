// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Used tokenRelationship.changeFrozenState (like in services)
 * 3. Used store.updateTokenRelationship(tokenRelationship)
 *    instead of tokenStore.commitTokenRelationships(List.of(tokenRelationship)) (like in services)
 */
public class UnfreezeLogic {

    public void unfreeze(final Id targetTokenId, final Id targetAccountId, final Store store) {
        /* --- Load the model objects --- */
        final var tokenRelationshipKey =
                new TokenRelationshipKey(targetTokenId.asEvmAddress(), targetAccountId.asEvmAddress());
        var tokenRelationship = store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW);

        /* --- Do the business logic --- */
        var unfrozentokenRelationship = tokenRelationship.changeFrozenState(false);

        /* --- Persist the updated models --- */
        store.updateTokenRelationship(unfrozentokenRelationship);
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        TokenUnfreezeAccountTransactionBody op = txnBody.getTokenUnfreeze();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        if (!op.hasAccount()) {
            return INVALID_ACCOUNT_ID;
        }

        return OK;
    }
}
