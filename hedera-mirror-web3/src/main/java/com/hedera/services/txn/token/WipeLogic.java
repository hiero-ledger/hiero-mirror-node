// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.services.txn.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hedera.services.txns.validation.ContextOptionValidator.batchSizeCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenModificationResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. validateSyntax method uses default value of true for areNftsEnabled property
 * 3. validateSyntax executes the logic directly instead of calling TokenWipeAccessor.validateSyntax
 * 4. Replaced GlobalDynamicProperties with MirrorNodeEvmProperties
 */
public class WipeLogic {
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public WipeLogic(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public TokenModificationResult wipe(
            final Id targetTokenId,
            final Id targetAccountId,
            final long amount,
            List<Long> serialNumbersList,
            final Store store) {
        // De-duplicate serial numbers
        serialNumbersList = new ArrayList<>(new LinkedHashSet<>(serialNumbersList));

        /* --- Load the model objects --- */
        final var token = store.getToken(targetTokenId.asEvmAddress(), OnMissing.THROW);
        final var tokenRelationshipKey =
                new TokenRelationshipKey(targetTokenId.asEvmAddress(), targetAccountId.asEvmAddress());
        final var accountRel = store.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW);

        /* --- Do the business logic --- */
        TokenModificationResult tokenModificationResult;
        if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
            tokenModificationResult = token.wipe(accountRel, amount);
        } else {
            final var tokenWithLoadedUniqueTokens = store.loadUniqueTokens(token, serialNumbersList);
            tokenModificationResult = tokenWithLoadedUniqueTokens.wipe(accountRel, serialNumbersList);
        }

        /* --- Persist the updated models --- */
        store.updateToken(tokenModificationResult.token());
        store.updateTokenRelationship(tokenModificationResult.tokenRelationship());
        store.updateAccount(tokenModificationResult.tokenRelationship().getAccount());

        return tokenModificationResult;
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        TokenWipeAccountTransactionBody body = txn.getTokenWipe();
        if (!body.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        if (!body.hasAccount()) {
            return INVALID_ACCOUNT_ID;
        }
        return validateTokenOpsWith(
                body.getSerialNumbersCount(),
                body.getAmount(),
                true,
                INVALID_WIPING_AMOUNT,
                body.getSerialNumbersList(),
                a -> batchSizeCheck(a, mirrorNodeEvmProperties.getMaxBatchSizeWipe()));
    }
}
