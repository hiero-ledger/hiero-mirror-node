// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.tokens;

import static com.hedera.node.app.service.evm.store.tokens.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.EntityIdUtils.toGrpcAccountId;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;

import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.jproto.JKey;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;

/**
 * Provides a managing store for arbitrary tokens. Differences with the original:
 * <ol>
 * <li>Removed validations performed in UsageLimits, since they check global node limits,
 * while on Archive Node we are interested in transaction scope only</li>
 * <li>Removed SideEffectsTracker and EntityIdSource</li>
 * <li>Use abstraction for the state by introducing {@link Store} interface</li>
 * <li>Use Mirror Node specific properties - {@link MirrorNodeEvmProperties}</li>
 * <li>Copied `usabilityOf` from `HederaLedger`</li>
 * <li>Renamed `updateLedgers()` to `updateStore()`</li>
 * </ol>
 */
public class HederaTokenStore {

    public static final TokenID MISSING_TOKEN = TokenID.getDefaultInstance();
    static final TokenID NO_PENDING_ID = TokenID.getDefaultInstance();
    private static final Predicate<Key> REMOVES_ADMIN_KEY = ImmutableKeyUtils::signalsKeyRemoval;
    private final OptionValidator validator;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final Store store;

    public HederaTokenStore(
            final OptionValidator validator, final MirrorNodeEvmProperties mirrorNodeEvmProperties, final Store store) {
        this.validator = validator;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
        this.store = store;
    }

    public static TokenRelationshipKey asTokenRelationshipKey(AccountID accountID, TokenID tokenID) {
        return new TokenRelationshipKey(asTypedEvmAddress(tokenID), asTypedEvmAddress(accountID));
    }

    public static boolean affectsExpiryAtMost(final TokenUpdateTransactionBody op) {
        return !op.hasAdminKey()
                && !op.hasKycKey()
                && !op.hasWipeKey()
                && !op.hasFreezeKey()
                && !op.hasSupplyKey()
                && !op.hasFeeScheduleKey()
                && !op.hasTreasury()
                && !op.hasPauseKey()
                && !op.hasAutoRenewAccount()
                && op.getSymbol().isEmpty()
                && op.getName().isEmpty()
                && op.getAutoRenewPeriod().getSeconds() == 0
                && !op.hasMemo();
    }

    protected ResponseCodeEnum checkAccountUsability(final AccountID aId) {
        var account = store.getAccount(asTypedEvmAddress(aId), OnMissing.DONT_THROW);

        if (account.isEmptyAccount()) {
            return INVALID_ACCOUNT_ID;
        }

        if (account.isDeleted()) {
            return ACCOUNT_DELETED;
        }
        return validator.expiryStatusGiven(store, aId);
    }

    private ResponseCodeEnum usabilityOf(final AccountID id) {
        try {
            final var account = store.getAccount(asTypedEvmAddress(id), OnMissing.THROW);
            final var isDeleted = account.isDeleted();
            if (isDeleted) {
                final var isContract = account.isSmartContract();
                return isContract ? CONTRACT_DELETED : ACCOUNT_DELETED;
            }
            return validator.expiryStatusGiven(store, id);
        } catch (final MissingEntityException ignore) {
            return INVALID_ACCOUNT_ID;
        }
    }

    public ResponseCodeEnum autoAssociate(AccountID aId, TokenID tId) {
        return fullySanityChecked(aId, tId, (accountId, tokenId) -> {
            final var tokenRelationship =
                    store.getTokenRelationship(asTokenRelationshipKey(aId, tId), OnMissing.DONT_THROW);

            if (!tokenRelationship.getAccount().getId().equals(Id.DEFAULT)) {
                return TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
            }

            var account = store.getAccount(asTypedEvmAddress(aId), OnMissing.THROW);
            var numAssociations = account.getNumAssociations();

            if (mirrorNodeEvmProperties.isLimitTokenAssociations()
                    && numAssociations == mirrorNodeEvmProperties.getMaxTokensPerAccount()) {
                return TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
            }

            if (!account.canAutoAssociate()) {
                return NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
            }

            final var token = get(tId);
            final var newTokenRelationship = new TokenRelationship(token, account)
                    .setFrozen(token.hasFreezeKey() && token.isFrozenByDefault())
                    .setKycGranted(!token.hasKycKey())
                    .setAutomaticAssociation(true);

            final var newAccount = account.autoAssociate();
            store.updateTokenRelationship(newTokenRelationship);
            store.updateAccount(newAccount);

            return OK;
        });
    }

    public boolean associationExists(final AccountID aId, final TokenID tId) {
        return checkExistence(aId, tId) == OK
                && !store.getTokenRelationship(asTokenRelationshipKey(aId, tId), OnMissing.DONT_THROW)
                        .isEmptyTokenRelationship();
    }

    public boolean exists(final TokenID id) {
        final var token = store.getToken(asTypedEvmAddress(id), OnMissing.DONT_THROW);
        return !token.getId().equals(Id.DEFAULT);
    }

    public Token get(final TokenID id) {
        final var token = store.getToken(asTypedEvmAddress(id), OnMissing.DONT_THROW);

        if (token.isEmptyToken()) {
            throw new IllegalArgumentException(
                    String.format("Argument 'id=%s' does not refer to a known token!", readableId(id)));
        }

        return token;
    }

    public void apply(final TokenID id, final UnaryOperator<Token> change) {
        final var token = store.getToken(asTypedEvmAddress(id), OnMissing.THROW);
        try {
            final var changedToken = change.apply(token);

            if (changedToken != null) {
                store.updateToken(changedToken);
            }
        } catch (Exception internal) {
            throw new IllegalArgumentException("Token change failed unexpectedly", internal);
        }
    }

    public ResponseCodeEnum grantKyc(final AccountID aId, final TokenID tId) {
        return setHasKyc(aId, tId, true);
    }

    public ResponseCodeEnum unfreeze(final AccountID aId, final TokenID tId) {
        return setIsFrozen(aId, tId, false);
    }

    private ResponseCodeEnum setHasKyc(final AccountID aId, final TokenID tId, final boolean value) {
        return sanityChecked(false, aId, null, tId, token -> {
            if (!token.hasKycKey()) {
                return TOKEN_HAS_NO_KYC_KEY;
            }

            final var tokenRelationship = store.getTokenRelationship(asTokenRelationshipKey(aId, tId), OnMissing.THROW);
            final var newTokenRelationship = tokenRelationship.setKycGranted(value);
            store.updateTokenRelationship(newTokenRelationship);
            return OK;
        });
    }

    private ResponseCodeEnum setIsFrozen(final AccountID aId, final TokenID tId, final boolean value) {
        return sanityChecked(false, aId, null, tId, token -> {
            if (!token.hasFreezeKey()) {
                return TOKEN_HAS_NO_FREEZE_KEY;
            }

            final var tokenRelationship = store.getTokenRelationship(asTokenRelationshipKey(aId, tId), OnMissing.THROW);
            final var newTokenRelationship = tokenRelationship.setFrozen(value);
            store.updateTokenRelationship(newTokenRelationship);
            return OK;
        });
    }

    public ResponseCodeEnum adjustBalance(final AccountID aId, final TokenID tId, final long adjustment) {
        return sanityCheckedFungibleCommon(aId, tId, token -> tryAdjustment(aId, tId, adjustment));
    }

    public ResponseCodeEnum changeOwner(final NftId nftId, final AccountID from, final AccountID to) {
        final var tId = nftId.tokenId();
        return sanityChecked(false, from, to, tId, token -> {
            final var nft = store.getUniqueToken(nftId, OnMissing.DONT_THROW);
            if (nft.getTokenId().equals(Id.DEFAULT)) {
                return INVALID_NFT_ID;
            }

            final var fromFreezeAndKycValidity = checkRelFrozenAndKycProps(from, tId);
            if (fromFreezeAndKycValidity != OK) {
                return fromFreezeAndKycValidity;
            }
            final var toFreezeAndKycValidity = checkRelFrozenAndKycProps(to, tId);
            if (toFreezeAndKycValidity != OK) {
                return toFreezeAndKycValidity;
            }

            final var tid = nftId.tokenId();
            final var tokenTreasury = store.getToken(asTypedEvmAddress(tid), OnMissing.THROW)
                    .getTreasury()
                    .getId();
            var owner = nft.getOwner();
            if (owner.equals(Id.DEFAULT)) {
                owner = tokenTreasury;
            }
            if (!owner.equals(Id.fromGrpcAccount(from))) {
                return SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
            }

            updateStore(nftId, from, to, toGrpcAccountId(tokenTreasury));
            return OK;
        });
    }

    private void updateStore(
            final NftId nftId, final AccountID from, final AccountID to, final AccountID tokenTreasury) {
        final var nftType = nftId.tokenId();
        final var fromRel = asTokenRelationshipKey(from, nftType);
        final var toRel = asTokenRelationshipKey(to, nftType);

        final var fromAccount = store.getAccount(asTypedEvmAddress(from), OnMissing.THROW);
        final var toAccount = store.getAccount(asTypedEvmAddress(to), OnMissing.THROW);
        final var fromRelation = store.getTokenRelationship(fromRel, OnMissing.THROW);
        final var toRelation = store.getTokenRelationship(toRel, OnMissing.THROW);

        final var fromNftsOwned = fromAccount.getOwnedNfts();
        final var fromThisNftsOwned = fromRelation.getBalance();
        final var toNftsOwned = toAccount.getOwnedNfts();
        final var toThisNftsOwned = toRelation.getBalance();
        final var fromNumPositiveBalances = fromAccount.getNumPositiveBalances();
        final var toNumPositiveBalances = toAccount.getNumPositiveBalances();
        final var isTreasuryReturn = tokenTreasury.equals(to);

        final var nft = store.getUniqueToken(nftId, OnMissing.THROW);
        UniqueToken newNft;
        if (isTreasuryReturn) {
            newNft = nft.setOwner(Id.DEFAULT);
        } else {
            newNft = nft.setOwner(Id.fromGrpcAccount(to));
        }
        store.updateUniqueToken(newNft);

        final var updatedFromPositiveBalances =
                fromThisNftsOwned - 1 == 0 ? fromNumPositiveBalances - 1 : fromNumPositiveBalances;
        final var updatedToNumPositiveBalances =
                toThisNftsOwned == 0 ? toNumPositiveBalances + 1 : toNumPositiveBalances;

        var newFromAccount =
                fromAccount.setOwnedNfts(fromNftsOwned - 1).setNumPositiveBalances(updatedFromPositiveBalances);

        var newToAccount = toAccount.setOwnedNfts(toNftsOwned + 1).setNumPositiveBalances(updatedToNumPositiveBalances);

        var newFromRelation = fromRelation.setBalance(fromThisNftsOwned - 1);
        var newToRelation = toRelation.setBalance(toThisNftsOwned + 1);

        // Note correctness here depends on rejecting self-exchanges
        store.updateAccount(newFromAccount);
        store.updateAccount(newToAccount);
        store.updateTokenRelationship(newFromRelation);
        store.updateTokenRelationship(newToRelation);
    }

    public ResponseCodeEnum changeOwnerWildCard(final NftId nftId, final AccountID from, final AccountID to) {
        final var tId = nftId.tokenId();
        return sanityChecked(false, from, to, tId, token -> {
            final var fromFreezeAndKycValidity = checkRelFrozenAndKycProps(from, tId);
            if (fromFreezeAndKycValidity != OK) {
                return fromFreezeAndKycValidity;
            }
            final var toFreezeAndKycValidity = checkRelFrozenAndKycProps(to, tId);
            if (toFreezeAndKycValidity != OK) {
                return toFreezeAndKycValidity;
            }

            final var nftType = nftId.tokenId();
            final var fromRel = asTokenRelationshipKey(from, nftType);
            final var toRel = asTokenRelationshipKey(to, nftType);

            final var fromAccount = store.getAccount(asTypedEvmAddress(from), OnMissing.THROW);
            final var toAccount = store.getAccount(asTypedEvmAddress(to), OnMissing.THROW);
            final var fromRelation = store.getTokenRelationship(fromRel, OnMissing.THROW);
            final var toRelation = store.getTokenRelationship(toRel, OnMissing.THROW);

            final var fromNftsOwned = fromAccount.getOwnedNfts();
            final var fromThisNftsOwned = fromRelation.getBalance();
            final var toNftsOwned = toAccount.getOwnedNfts();
            final var toThisNftsOwned = toRelation.getBalance();

            final var newFromAccount = fromAccount.setOwnedNfts(fromNftsOwned - fromThisNftsOwned);
            final var newToAccount = toAccount.setOwnedNfts(toNftsOwned + fromThisNftsOwned);
            final var newFromRelation = fromRelation.setBalance(0);
            final var newToRelation = toRelation.setBalance(toThisNftsOwned + fromThisNftsOwned);

            store.updateAccount(newFromAccount);
            store.updateAccount(newToAccount);
            store.updateTokenRelationship(newFromRelation);
            store.updateTokenRelationship(newToRelation);

            return OK;
        });
    }

    public boolean matchesTokenDecimals(final TokenID tId, final int expectedDecimals) {
        return get(tId).getDecimals() == expectedDecimals;
    }

    private ResponseCodeEnum tryAdjustment(final AccountID aId, final TokenID tId, final long adjustment) {
        final var freezeAndKycValidity = checkRelFrozenAndKycProps(aId, tId);
        if (!freezeAndKycValidity.equals(OK)) {
            return freezeAndKycValidity;
        }

        final var relationship = asTokenRelationshipKey(aId, tId);

        final var tokenRelationship = store.getTokenRelationship(relationship, OnMissing.THROW);

        final var balance = tokenRelationship.getBalance();
        final var newBalance = balance + adjustment;
        if (newBalance < 0) {
            return INSUFFICIENT_TOKEN_BALANCE;
        }

        final var newTokenRelationship = tokenRelationship.setBalance(newBalance);
        store.updateTokenRelationship(newTokenRelationship);

        final var account = store.getAccount(asTypedEvmAddress(aId), OnMissing.THROW);
        int numPositiveBalances = account.getNumPositiveBalances();

        // If the original balance is zero, then the receiving account's numPositiveBalances has to
        // be increased
        // and if the newBalance is zero, then the sending account's numPositiveBalances has to be
        // decreased
        if (newBalance == 0 && adjustment < 0) {
            numPositiveBalances--;
        } else if (balance == 0 && adjustment > 0) {
            numPositiveBalances++;
        }

        final var newAccount = account.setNumPositiveBalances(numPositiveBalances);
        store.updateAccount(newAccount);
        return OK;
    }

    private ResponseCodeEnum checkRelFrozenAndKycProps(final AccountID aId, final TokenID tId) {
        final var relationship = asTokenRelationshipKey(aId, tId);
        final var tokenRelationship = store.getTokenRelationship(relationship, OnMissing.THROW);

        if (tokenRelationship.isFrozen()) {
            return ACCOUNT_FROZEN_FOR_TOKEN;
        }
        if (!tokenRelationship.isKycGranted()) {
            return ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
        }
        return OK;
    }

    private boolean isValidAutoRenewPeriod(final long secs) {
        return validator.isValidAutoRenewPeriod(
                Duration.newBuilder().setSeconds(secs).build());
    }

    @SuppressWarnings("java:S1172")
    public ResponseCodeEnum update(final TokenUpdateTransactionBody changes, final long now) {
        final var tId = resolve(changes.getToken());
        if (tId == MISSING_TOKEN) {
            return INVALID_TOKEN_ID;
        }
        ResponseCodeEnum validity;
        final var isExpiryOnly = affectsExpiryAtMost(changes);

        validity = checkAutoRenewAccount(changes);
        if (validity != OK) {
            return validity;
        }

        final var newKycKey = changes.hasKycKey() ? asUsableFcKey(changes.getKycKey()) : Optional.empty();
        final var newWipeKey = changes.hasWipeKey() ? asUsableFcKey(changes.getWipeKey()) : Optional.empty();
        final var newSupplyKey = changes.hasSupplyKey() ? asUsableFcKey(changes.getSupplyKey()) : Optional.empty();
        final var newFreezeKey = changes.hasFreezeKey() ? asUsableFcKey(changes.getFreezeKey()) : Optional.empty();
        final var newFeeScheduleKey =
                changes.hasFeeScheduleKey() ? asUsableFcKey(changes.getFeeScheduleKey()) : Optional.empty();
        final var newPauseKey = changes.hasPauseKey() ? asUsableFcKey(changes.getPauseKey()) : Optional.empty();

        var appliedValidity = new AtomicReference<>(OK);
        apply(tId, token -> {
            processExpiry(appliedValidity, changes, token);
            processAutoRenewAccount(appliedValidity, changes, token);

            checkKeyOfType(appliedValidity, token.hasKycKey(), newKycKey.isPresent(), TOKEN_HAS_NO_KYC_KEY);
            checkKeyOfType(appliedValidity, token.hasFreezeKey(), newFreezeKey.isPresent(), TOKEN_HAS_NO_FREEZE_KEY);
            checkKeyOfType(appliedValidity, token.hasPauseKey(), newPauseKey.isPresent(), TOKEN_HAS_NO_PAUSE_KEY);
            checkKeyOfType(appliedValidity, token.hasWipeKey(), newWipeKey.isPresent(), TOKEN_HAS_NO_WIPE_KEY);
            checkKeyOfType(appliedValidity, token.hasSupplyKey(), newSupplyKey.isPresent(), TOKEN_HAS_NO_SUPPLY_KEY);
            checkKeyOfType(appliedValidity, token.hasAdminKey(), !isExpiryOnly, TOKEN_IS_IMMUTABLE);
            checkKeyOfType(
                    appliedValidity,
                    token.hasFeeScheduleKey(),
                    newFeeScheduleKey.isPresent(),
                    TOKEN_HAS_NO_FEE_SCHEDULE_KEY);
            if (OK != appliedValidity.get()) {
                return null;
            }

            final var ret = checkNftBalances(token, tId, changes);
            if (ret != OK) {
                appliedValidity.set(ret);
                return null;
            }

            token = updateAdminKeyIfAppropriate(token, changes);
            token = updateAutoRenewAccountIfAppropriate(token, changes);
            token = updateAutoRenewPeriodIfAppropriate(token, changes);

            token = updateKeyOfTypeIfAppropriate(
                    changes.hasFreezeKey(), token::setFreezeKey, changes::getFreezeKey, token);
            token = updateKeyOfTypeIfAppropriate(changes.hasKycKey(), token::setKycKey, changes::getKycKey, token);
            token = updateKeyOfTypeIfAppropriate(
                    changes.hasPauseKey(), token::setPauseKey, changes::getPauseKey, token);
            token = updateKeyOfTypeIfAppropriate(
                    changes.hasSupplyKey(), token::setSupplyKey, changes::getSupplyKey, token);
            token = updateKeyOfTypeIfAppropriate(changes.hasWipeKey(), token::setWipeKey, changes::getWipeKey, token);
            token = updateKeyOfTypeIfAppropriate(
                    changes.hasFeeScheduleKey(), token::setFeeScheduleKey, changes::getFeeScheduleKey, token);

            token = updateTokenSymbolIfAppropriate(token, changes);
            token = updateTokenNameIfAppropriate(token, changes);
            token = updateTreasuryIfAppropriate(token, changes);
            token = updateMemoIfAppropriate(token, changes);
            token = updateExpiryIfAppropriate(token, changes);
            return token;
        });
        return appliedValidity.get();
    }

    public ResponseCodeEnum updateExpiryInfo(final TokenUpdateTransactionBody changes) {
        final var tId = resolve(changes.getToken());
        if (tId == MISSING_TOKEN) {
            return INVALID_TOKEN_ID;
        }
        ResponseCodeEnum validity;

        validity = checkAutoRenewAccount(changes);
        if (validity != OK) {
            return validity;
        }

        var appliedValidity = new AtomicReference<>(OK);
        apply(tId, token -> {
            processExpiry(appliedValidity, changes, token);
            processAutoRenewAccount(appliedValidity, changes, token);

            if (OK != appliedValidity.get()) {
                return null;
            }

            token = updateAutoRenewAccountIfAppropriate(token, changes);
            token = updateAutoRenewPeriodIfAppropriate(token, changes);
            token = updateExpiryIfAppropriate(token, changes);
            return token;
        });
        return appliedValidity.get();
    }

    private ResponseCodeEnum checkAutoRenewAccount(final TokenUpdateTransactionBody changes) {
        ResponseCodeEnum validity = OK;
        if (changes.hasAutoRenewAccount()) {
            validity = usableOrElse(changes.getAutoRenewAccount(), INVALID_AUTORENEW_ACCOUNT);
            if (validity != OK) {
                return validity;
            }
        }
        return validity;
    }

    private void processExpiry(
            final AtomicReference<ResponseCodeEnum> appliedValidity,
            final TokenUpdateTransactionBody changes,
            final Token token) {
        final var candidateExpiry = changes.getExpiry().getSeconds();
        if (candidateExpiry != 0 && candidateExpiry < token.getExpiry()) {
            appliedValidity.set(INVALID_EXPIRATION_TIME);
        }
    }

    private void checkKeyOfType(
            final AtomicReference<ResponseCodeEnum> appliedValidity,
            final boolean hasKey,
            final boolean keyPresentOrExpiryOnly,
            final ResponseCodeEnum code) {
        if (!hasKey && keyPresentOrExpiryOnly) {
            appliedValidity.set(code);
        }
    }

    private void processAutoRenewAccount(
            final AtomicReference<ResponseCodeEnum> appliedValidity,
            final TokenUpdateTransactionBody changes,
            final Token token) {
        if (changes.hasAutoRenewAccount() || token.getAutoRenewAccount() != null) {
            final long changedAutoRenewPeriod = changes.getAutoRenewPeriod().getSeconds();
            if ((changedAutoRenewPeriod != 0 || token.getAutoRenewAccount() == null)
                    && !isValidAutoRenewPeriod(changedAutoRenewPeriod)) {
                appliedValidity.set(INVALID_RENEWAL_PERIOD);
            }
        }
    }

    private ResponseCodeEnum checkNftBalances(
            final Token token, final TokenID tId, final TokenUpdateTransactionBody changes) {
        if (token.getType().equals(TokenType.NON_FUNGIBLE_UNIQUE)
                && changes.hasTreasury()
                && !changes.getTreasury().equals(token.getTreasury().getId().asGrpcAccount())) {
            /* This relationship is verified to exist in the TokenUpdateTransitionLogic */
            final var newTreasuryRel = asTokenRelationshipKey(changes.getTreasury(), tId);
            final var relation = store.getTokenRelationship(newTreasuryRel, OnMissing.THROW);
            final var balance = relation.getBalance();
            if (balance != 0) {
                return TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
            }
        }
        return OK;
    }

    private Token updateAdminKeyIfAppropriate(final Token token, final TokenUpdateTransactionBody changes) {
        Token updatedToken = token;
        if (changes.hasAdminKey()) {
            final var newAdminKey = changes.getAdminKey();
            if (REMOVES_ADMIN_KEY.test(newAdminKey)) {
                updatedToken = token.setAdminKey(null);
            } else {
                updatedToken = token.setAdminKey(asFcKeyUnchecked(newAdminKey));
            }
        }
        return updatedToken;
    }

    private Token updateAutoRenewAccountIfAppropriate(final Token token, final TokenUpdateTransactionBody changes) {
        Token updatedToken = token;
        if (changes.hasAutoRenewAccount()) {
            final var account = store.getAccount(asTypedEvmAddress(changes.getAutoRenewAccount()), OnMissing.THROW);
            updatedToken = token.setAutoRenewAccount(account);
        }
        return updatedToken;
    }

    private Token updateAutoRenewPeriodIfAppropriate(final Token token, final TokenUpdateTransactionBody changes) {
        Token updatedToken = token;
        if (token.getAutoRenewAccount() != null) {
            final long changedAutoRenewPeriod = changes.getAutoRenewPeriod().getSeconds();
            if (changedAutoRenewPeriod > 0) {
                updatedToken = token.setAutoRenewPeriod(changedAutoRenewPeriod);
            }
        }
        return updatedToken;
    }

    private Token updateTokenSymbolIfAppropriate(final Token token, final TokenUpdateTransactionBody changes) {
        Token updatedToken = token;
        if (!changes.getSymbol().isEmpty()) {
            updatedToken = token.setSymbol(changes.getSymbol());
        }
        return updatedToken;
    }

    private Token updateTokenNameIfAppropriate(final Token token, final TokenUpdateTransactionBody changes) {
        Token updatedToken = token;
        if (!changes.getName().isEmpty()) {
            updatedToken = token.setName(changes.getName());
        }
        return updatedToken;
    }

    private Token updateMemoIfAppropriate(final Token token, final TokenUpdateTransactionBody changes) {
        Token updatedToken = token;
        if (changes.hasMemo()) {
            updatedToken = token.setMemo(changes.getMemo().getValue());
        }
        return updatedToken;
    }

    private Token updateExpiryIfAppropriate(final Token token, final TokenUpdateTransactionBody changes) {
        Token updatedToken = token;
        final var expiry = changes.getExpiry().getSeconds();
        if (expiry != 0) {
            updatedToken = token.setExpiry(expiry);
        }
        return updatedToken;
    }

    private Token updateTreasuryIfAppropriate(final Token token, final TokenUpdateTransactionBody changes) {
        Token updatedToken = token;
        if (changes.hasTreasury()
                && !changes.getTreasury().equals(token.getTreasury().getId().asGrpcAccount())) {
            final var treasuryId = changes.getTreasury();
            final var treasury = store.getAccount(asTypedEvmAddress(treasuryId), OnMissing.THROW);
            updatedToken = token.setTreasury(treasury);
        }
        return updatedToken;
    }

    private Token updateKeyOfTypeIfAppropriate(
            final boolean check, final Function<JKey, Token> consumer, Supplier<Key> supplier, Token token) {
        Token updatedToken = token;
        if (check) {
            updatedToken = consumer.apply(asFcKeyUnchecked(supplier.get()));
        }
        return updatedToken;
    }

    private ResponseCodeEnum fullySanityChecked(
            final AccountID aId, final TokenID tId, final BiFunction<AccountID, TokenID, ResponseCodeEnum> action) {
        final var validity = usabilityOf(aId);
        if (validity != OK) {
            return validity;
        }
        final var id = resolve(tId);
        if (id == MISSING_TOKEN) {
            return INVALID_TOKEN_ID;
        }
        final var token = get(id);
        if (token.isDeleted()) {
            return TOKEN_WAS_DELETED;
        }
        return action.apply(aId, tId);
    }

    private ResponseCodeEnum sanityCheckedFungibleCommon(
            final AccountID aId, final TokenID tId, final Function<Token, ResponseCodeEnum> action) {
        return sanityChecked(true, aId, null, tId, action);
    }

    @SuppressWarnings("java:S3776")
    private ResponseCodeEnum sanityChecked(
            final boolean onlyFungibleCommon,
            final AccountID aId,
            final AccountID aCounterPartyId,
            final TokenID tId,
            final Function<Token, ResponseCodeEnum> action) {
        var validity = checkAccountUsability(aId);
        if (validity != OK) {
            return validity;
        }
        if (aCounterPartyId != null) {
            validity = checkAccountUsability(aCounterPartyId);
            if (validity != OK) {
                return validity;
            }
        }

        validity = checkTokenExistence(tId);
        if (validity != OK) {
            return validity;
        }

        final var token = get(tId);
        if (token.isDeleted()) {
            return TOKEN_WAS_DELETED;
        }
        if (token.isPaused()) {
            return TOKEN_IS_PAUSED;
        }
        if (onlyFungibleCommon && token.getType() == NON_FUNGIBLE_UNIQUE) {
            return ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
        }

        var key = asTokenRelationshipKey(aId, tId);
        var tokenRelationship = store.getTokenRelationship(key, OnMissing.DONT_THROW);

        /*
         * Instead of returning  TOKEN_NOT_ASSOCIATED_TO_ACCOUNT when a token is not associated,
         * we check if the account has any maxAutoAssociations set up, if they do check if we reached the limit and
         * auto associate. If not return EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT
         */
        if (tokenRelationship.getAccount().getId().equals(Id.DEFAULT)) {
            validity = validateAndAutoAssociate(aId, tId);
            if (validity != OK) {
                return validity;
            }
        }
        if (aCounterPartyId != null) {
            key = asTokenRelationshipKey(aCounterPartyId, tId);
            tokenRelationship = store.getTokenRelationship(key, OnMissing.DONT_THROW);
            if (tokenRelationship.getAccount().getId().equals(Id.DEFAULT)) {
                validity = validateAndAutoAssociate(aCounterPartyId, tId);
                if (validity != OK) {
                    return validity;
                }
            }
        }

        return action.apply(token);
    }

    private ResponseCodeEnum validateAndAutoAssociate(AccountID aId, TokenID tId) {
        final var account = store.getAccount(asTypedEvmAddress(aId), OnMissing.THROW);
        if (account.isAutoAssociateEnabled()) {
            return autoAssociate(aId, tId);
        }
        return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
    }

    private ResponseCodeEnum checkTokenExistence(final TokenID tId) {
        return exists(tId) ? OK : INVALID_TOKEN_ID;
    }

    public TokenID resolve(TokenID id) {
        return exists(id) ? id : MISSING_TOKEN;
    }

    public ResponseCodeEnum tryTokenChange(BalanceChange change) {
        var validity = OK;
        var tokenId = resolve(change.tokenId());
        if (tokenId == MISSING_TOKEN) {
            validity = INVALID_TOKEN_ID;
        }
        if (change.hasExpectedDecimals() && !matchesTokenDecimals(change.tokenId(), change.getExpectedDecimals())) {
            validity = UNEXPECTED_TOKEN_DECIMALS;
        }
        if (validity == OK) {
            if (change.isForNft()) {
                validity = changeOwner(change.nftId(), change.accountId(), change.counterPartyAccountId());
            } else {
                validity = adjustBalance(change.accountId(), tokenId, change.getAggregatedUnits());
                if (validity == INSUFFICIENT_TOKEN_BALANCE) {
                    validity = change.codeForInsufficientBalance();
                }
            }
        }
        return validity;
    }

    protected ResponseCodeEnum usableOrElse(AccountID aId, ResponseCodeEnum fallbackFailure) {
        final var validity = checkAccountUsability(aId);

        return (validity == ACCOUNT_EXPIRED_AND_PENDING_REMOVAL || validity == OK) ? validity : fallbackFailure;
    }

    private ResponseCodeEnum checkExistence(final AccountID aId, final TokenID tId) {
        final var validity = checkAccountUsability(aId);
        if (validity != OK) {
            return validity;
        }
        return exists(tId) ? OK : INVALID_TOKEN_ID;
    }
}
