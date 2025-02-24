// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static com.hedera.services.txn.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;

import com.google.protobuf.ByteString;
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
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.List;

/**
 * Copied Logic type from hedera-services.
 *
 * Differences with the original:
 *  1. Removed validations performed in UsageLimits, since they check global node limits,
 *  while on Archive Node we are interested in transaction scope only
 *  2. Use abstraction for the state by introducing {@link Store} interface
 *  3. Use copied models from hedera-services which are enhanced with additional constructors for easier setup,
 *  those are {@link Account}, {@link Token}, {@link TokenRelationship}
 *  4. validateSyntax method uses default value of true for areNftsEnabled property
 * */
public class MintLogic {

    private final OptionValidator validator;

    public MintLogic(final OptionValidator validator) {
        this.validator = validator;
    }

    public TokenModificationResult mint(
            final Id targetId,
            final long amount,
            final List<ByteString> metaDataList,
            final Instant consensusTime,
            final Store store) {

        /* --- Load the model objects --- */
        final var token = store.getToken(targetId.asEvmAddress(), OnMissing.THROW);

        final var tokenRelationshipKey = new TokenRelationshipKey(
                token.getId().asEvmAddress(), token.getTreasury().getAccountAddress());
        final var treasuryRel = store.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW);

        TokenModificationResult tokenModificationResult;
        /* --- Do the business logic --- */
        if (token.getType() == TokenType.FUNGIBLE_COMMON) {
            tokenModificationResult = token.mint(treasuryRel, amount, false);
        } else {
            tokenModificationResult = token.mint(treasuryRel, metaDataList, fromJava(consensusTime));
        }

        /* --- Persist the updated models --- */
        store.updateToken(tokenModificationResult.token());
        store.updateTokenRelationship(tokenModificationResult.tokenRelationship());
        store.updateAccount(tokenModificationResult.token().getTreasury());

        return tokenModificationResult;
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        TokenMintTransactionBody op = txn.getTokenMint();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return validateTokenOpsWith(
                op.getMetadataCount(),
                op.getAmount(),
                true,
                INVALID_TOKEN_MINT_AMOUNT,
                op.getMetadataList(),
                validator::maxBatchSizeMintCheck,
                validator::nftMetadataCheck);
    }
}
