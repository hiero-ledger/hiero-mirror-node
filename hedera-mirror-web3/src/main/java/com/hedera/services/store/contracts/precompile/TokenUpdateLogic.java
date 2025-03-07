// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.node.app.service.evm.store.tokens.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalseOrRevert;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.store.tokens.HederaTokenStore.MISSING_TOKEN;
import static com.hedera.services.store.tokens.HederaTokenStore.affectsExpiryAtMost;
import static com.hedera.services.store.tokens.HederaTokenStore.asTokenRelationshipKey;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.google.protobuf.StringValue;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.util.TokenUpdateValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;

/**
 * Copied Logic type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Get allowChangedTreasuryToOwnNfts from mirrorNodeEvmProperties
 */
public class TokenUpdateLogic {
    private final OptionValidator validator;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public TokenUpdateLogic(MirrorNodeEvmProperties mirrorNodeEvmProperties, OptionValidator validator) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
        this.validator = validator;
    }

    public void updateToken(TokenUpdateTransactionBody op, long now, Store store, HederaTokenStore tokenStore) {
        updateToken(op, now, false, store, tokenStore);
    }

    /**
     * Given a token update transaction and the current consensus time, updates the token in the store.
     *
     * <p>The third {@code mergeUnsetMemoFromExisting} argument says whether to preserve the token's
     * existing memo if the transaction memo is empty. We need this because the current application
     * binary interface (ABI) for the {@code tokenUpdate()} system contract does not let us distinguish
     * between a contract omitting the memo in an token update vs. a contract explicitly setting the
     * memo to the empty string.
     *
     * <p>Once the ABI is improved to let a contract advertise it truly does want to erase a memo,
     * we can remove the {@code mergeUnsetMemoFromExisting} argument and just use the protobuf
     * message's {@code hasMemo()} method.
     *
     * @param op                         the token update transaction
     * @param now                        the current consensus time
     * @param mergeUnsetMemoFromExisting whether to preserve the token's memo if the transaction memo is unset
     */
    @SuppressWarnings("java:S3776")
    public void updateToken(
            TokenUpdateTransactionBody op,
            long now,
            boolean mergeUnsetMemoFromExisting,
            Store store,
            HederaTokenStore tokenStore) {
        final var tokenID = tokenValidityCheck(op);
        if (op.hasExpiry()) {
            validateTrueOrRevert(validator.isValidExpiry(op.getExpiry()), INVALID_EXPIRATION_TIME);
        }
        final var token = tokenStore.get(tokenID);
        final var isOpMemoUnset = !op.hasMemo() || op.getMemo().getValue().isEmpty();
        if (isOpMemoUnset && mergeUnsetMemoFromExisting) {
            final var existingMemo = Optional.ofNullable(token.getMemo()).orElse("");
            op = op.toBuilder().setMemo(StringValue.of(existingMemo)).build();
        }
        checkTokenPreconditions(token, op);

        assertAutoRenewValidity(op, token, store);
        Optional<AccountID> replacedTreasury = Optional.empty();
        ResponseCodeEnum outcome;
        if (op.hasTreasury()) {
            var newTreasury = op.getTreasury();
            validateFalseOrRevert(isDetached(newTreasury, store), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

            if (!tokenStore.associationExists(newTreasury, tokenID)) {
                outcome = tokenStore.autoAssociate(newTreasury, tokenID);
                if (outcome != OK) {
                    abortWith(outcome);
                }
            }
            var existingTreasury = token.getTreasury().getId().asGrpcAccount();
            if (!mirrorNodeEvmProperties.isAllowTreasuryToOwnNfts() && token.getType() == NON_FUNGIBLE_UNIQUE) {
                var existingTreasuryBalance = getTokenBalance(existingTreasury, tokenID, store);
                if (existingTreasuryBalance > 0L) {
                    abortWith(CURRENT_TREASURY_STILL_OWNS_NFTS);
                }
            }
            if (!newTreasury.equals(existingTreasury)) {
                validateFalseOrRevert(isDetached(existingTreasury, store), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

                outcome = prepTreasuryChange(tokenID, token, newTreasury, existingTreasury, store, tokenStore);
                if (outcome != OK) {
                    abortWith(outcome);
                }
                replacedTreasury = Optional.of(token.getTreasury().getId().asGrpcAccount());
            }
        }

        outcome = tokenStore.update(op, now);
        if (outcome == OK && replacedTreasury.isPresent()) {
            final var oldTreasury = replacedTreasury.get();
            long replacedTreasuryBalance = getTokenBalance(oldTreasury, tokenID, store);
            if (replacedTreasuryBalance > 0) {
                if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
                    outcome = doTokenTransfer(
                            tokenID, oldTreasury, op.getTreasury(), replacedTreasuryBalance, tokenStore);
                } else {
                    outcome = tokenStore.changeOwnerWildCard(
                            new NftId(tokenID.getShardNum(), tokenID.getRealmNum(), tokenID.getTokenNum(), -1),
                            oldTreasury,
                            op.getTreasury());
                }
            }
        }
        if (outcome != OK) {
            abortWith(outcome);
        }
    }

    public void updateTokenExpiryInfo(TokenUpdateTransactionBody op, Store store, HederaTokenStore tokenStore) {
        final var tokenID = tokenStore.resolve(op.getToken());
        validateTrueOrRevert(!tokenID.equals(MISSING_TOKEN), INVALID_TOKEN_ID);
        if (op.hasExpiry()) {
            validateTrueOrRevert(validator.isValidExpiry(op.getExpiry()), INVALID_EXPIRATION_TIME);
        }
        Token token = tokenStore.get(tokenID);
        checkTokenPreconditions(token, op);
        assertAutoRenewValidity(op, token, store);

        var outcome = tokenStore.updateExpiryInfo(op);
        if (outcome != OK) {
            abortWith(outcome);
        }
    }

    public void updateTokenKeys(TokenUpdateTransactionBody op, long now, HederaTokenStore tokenStore) {
        final var tokenID = tokenValidityCheck(op);
        Token token = tokenStore.get(tokenID);
        checkTokenPreconditions(token, op);
        final var outcome = tokenStore.update(op, now);

        if (outcome != OK) {
            abortWith(outcome);
        }
    }

    private TokenID tokenValidityCheck(TokenUpdateTransactionBody op) {
        final var tokenID = Id.fromGrpcToken(op.getToken()).asGrpcToken();
        validateFalse(tokenID.equals(MISSING_TOKEN), INVALID_TOKEN_ID);
        return tokenID;
    }

    private void checkTokenPreconditions(Token token, TokenUpdateTransactionBody op) {
        if (!token.hasAdminKey()) validateTrueOrRevert((affectsExpiryAtMost(op)), TOKEN_IS_IMMUTABLE);
        validateFalseOrRevert(token.isDeleted(), TOKEN_WAS_DELETED);
        validateFalseOrRevert(token.isPaused(), TOKEN_IS_PAUSED);
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        return TokenUpdateValidator.validate(txnBody, validator);
    }

    private void assertAutoRenewValidity(TokenUpdateTransactionBody op, Token token, Store store) {
        if (op.hasAutoRenewAccount()) {
            final var newAutoRenew = op.getAutoRenewAccount();
            final var newAutoRenewAccount = store.getAccount(asTypedEvmAddress(newAutoRenew), OnMissing.DONT_THROW);
            validateTrueOrRevert(!newAutoRenewAccount.isEmptyAccount(), INVALID_AUTORENEW_ACCOUNT);
            validateFalseOrRevert(isDetached(newAutoRenew, store), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

            if (token.hasAutoRenewAccount()) {
                final var existingAutoRenew =
                        token.getAutoRenewAccount().getId().asGrpcAccount();
                validateFalseOrRevert(isDetached(existingAutoRenew, store), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
            }
        }
    }

    private boolean isDetached(AccountID accountID, Store store) {
        return validator.expiryStatusGiven(store, accountID) != OK;
    }

    private ResponseCodeEnum prepTreasuryChange(
            final TokenID id,
            final Token token,
            final AccountID newTreasury,
            final AccountID oldTreasury,
            Store store,
            HederaTokenStore tokenStore) {
        var status = OK;
        if (token.hasFreezeKey()) {
            status = tokenStore.unfreeze(newTreasury, id);
        }
        if (status == OK && token.hasKycKey()) {
            status = tokenStore.grantKyc(newTreasury, id);
        }
        if (status == OK) {
            decrementNumTreasuryTitles(oldTreasury, store);
            incrementNumTreasuryTitles(newTreasury, store);
        }
        return status;
    }

    private void abortWith(ResponseCodeEnum cause) {
        throw new InvalidTransactionException(cause);
    }

    private ResponseCodeEnum doTokenTransfer(
            TokenID tId, AccountID from, AccountID to, long adjustment, HederaTokenStore tokenStore) {
        ResponseCodeEnum validity = tokenStore.adjustBalance(from, tId, -adjustment);
        if (validity == OK) {
            validity = tokenStore.adjustBalance(to, tId, adjustment);
        }

        return validity;
    }

    public long getTokenBalance(AccountID aId, TokenID tId, Store store) {
        var relationship = asTokenRelationshipKey(aId, tId);
        var rel = store.getTokenRelationship(relationship, OnMissing.THROW);
        return rel.getBalance();
    }

    private void incrementNumTreasuryTitles(final AccountID aId, Store store) {
        changeNumTreasuryTitles(aId, +1, store);
    }

    private void decrementNumTreasuryTitles(final AccountID aId, Store store) {
        changeNumTreasuryTitles(aId, -1, store);
    }

    private void changeNumTreasuryTitles(final AccountID aId, final int delta, Store store) {
        final var account = store.getAccount(asTypedEvmAddress(aId), OnMissing.THROW);
        final var updatedAccount = account.setNumTreasuryTitles(account.getNumTreasuryTitles() + delta);
        store.updateAccount(updatedAccount);
    }
}
