// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.services.txn.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Removed validations performed in UsageLimits, since they check global node limits,
 * while on Archive Node we are interested in transaction scope only
 * 2. Use abstraction for the state by introducing {@link Store} interface
 * 3. Use copied models from hedera-services which are enhanced with additional constructors for easier setup,
 * those are {@link Account}, {@link Token}, {@link TokenRelationship}
 * 4. validateSyntax method uses default value of true for areNftsEnabled property
 */
public class BurnLogic {

    private final OptionValidator validator;

    public BurnLogic(final OptionValidator validator) {
        this.validator = validator;
    }

    public TokenModificationResult burn(
            final Id targetId, final long amount, List<Long> serialNumbersList, final Store store) {
        // De-duplicate serial numbers
        serialNumbersList = new ArrayList<>(new LinkedHashSet<>(serialNumbersList));

        /* --- Load the models --- */
        final var token = store.getToken(targetId.asEvmAddress(), OnMissing.THROW);
        final var tokenRelationshipKey = new TokenRelationshipKey(
                token.getId().asEvmAddress(), token.getTreasury().getAccountAddress());
        final var treasuryRel = store.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW);

        /* --- Do the business logic --- */
        TokenModificationResult tokenModificationResult;
        if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
            tokenModificationResult = token.burn(treasuryRel, amount);
        } else {
            final var tokenWithLoadedUniqueTokens = store.loadUniqueTokens(token, serialNumbersList);
            tokenModificationResult = tokenWithLoadedUniqueTokens.burn(treasuryRel, serialNumbersList);
        }

        store.updateToken(tokenModificationResult.token());
        store.updateTokenRelationship(tokenModificationResult.tokenRelationship());
        store.updateAccount(tokenModificationResult.token().getTreasury());

        return tokenModificationResult;
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        final TokenBurnTransactionBody op = txn.getTokenBurn();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return validateTokenOpsWith(
                op.getSerialNumbersCount(),
                op.getAmount(),
                true,
                INVALID_TOKEN_BURN_AMOUNT,
                op.getSerialNumbersList(),
                validator::maxBatchSizeBurnCheck);
    }
}
