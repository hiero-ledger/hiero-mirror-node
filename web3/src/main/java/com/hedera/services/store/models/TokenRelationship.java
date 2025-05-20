// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Copied model from hedera-services.
 *
 * Encapsulates the state and operations of a Hedera account-token relationship.
 *
 * <p>Operations are validated, and throw a {@link InvalidTransactionException} with response code
 * capturing the failure when one occurs.
 *
 * <p><b>NOTE:</b> Some operations will likely be moved to specializations of this class as NFTs are
 * fully supported. For example, a {@link TokenRelationship#getBalanceChange()} signature only makes sense for a token
 * of type {@code FUNGIBLE_COMMON}; the analogous signature for a {@code NON_FUNGIBLE_UNIQUE} is
 * {@code getOwnershipChanges())}, returning a type that is structurally equivalent to a {@code Pair<long[], long[]>} of
 * acquired and relinquished serial numbers.
 *
 * This model is used as a value in a special state (CachingStateFrame), used for speculative write operations. Object
 * immutability is required for this model in order to be used seamlessly in the state.
 *
 * Differences from the original:
 *  1. Added factory method that returns empty instance
 *  2. Added isEmptyTokenRelationship() method
 */
public class TokenRelationship {
    private final Token token;
    private final Account account;
    private final Supplier<Long> balance;
    private final boolean frozen;
    private final boolean kycGranted;
    private final boolean destroyed;
    private final boolean notYetPersisted;
    private final boolean automaticAssociation;
    private final long balanceChange;

    @SuppressWarnings("java:S107")
    public TokenRelationship(
            Token token,
            Account account,
            Supplier<Long> balance,
            boolean frozen,
            boolean kycGranted,
            boolean destroyed,
            boolean notYetPersisted,
            boolean automaticAssociation,
            long balanceChange) {
        this.token = token;
        this.account = account;
        this.balance = balance;
        this.frozen = frozen;
        this.kycGranted = kycGranted;
        this.destroyed = destroyed;
        this.notYetPersisted = notYetPersisted;
        this.automaticAssociation = automaticAssociation;
        this.balanceChange = balanceChange;
    }

    public TokenRelationship(Token token, Account account) {
        this(
                token,
                account,
                () -> 0L,
                token.isFrozenByDefault() && token.hasFreezeKey(),
                !token.hasKycKey(),
                false,
                true,
                false,
                0);
    }

    public static TokenRelationship getEmptyTokenRelationship() {
        return new TokenRelationship(new Token(Id.DEFAULT), new Account(0L, Id.DEFAULT, 0L));
    }

    public boolean isEmptyTokenRelationship() {
        return this.equals(getEmptyTokenRelationship());
    }

    /**
     * Modifies the state of the "Frozen" property to either true (freezes the relation between the
     * account and the token) or false (unfreezes the relation between the account and the token).
     *
     * <p>Before the property modification, the method performs validation, that the respective
     * token has a freeze key.
     *
     * @param freeze the new state of the property
     */
    public TokenRelationship changeFrozenState(boolean freeze) {
        validateTrue(token.hasFreezeKey(), TOKEN_HAS_NO_FREEZE_KEY);
        return createNewTokenRelationshipWithFrozenFlag(this, freeze);
    }

    /**
     * Creates new instance of {@link TokenRelationship} with updated balance in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldTokenRel
     * @param balanceChange
     * @param balance
     * @return new instance of {@link TokenRelationship}
     */
    private TokenRelationship createNewTokenRelationshipWithNewBalance(
            TokenRelationship oldTokenRel, long balanceChange, long balance) {
        return new TokenRelationship(
                oldTokenRel.token,
                oldTokenRel.account,
                () -> balance,
                oldTokenRel.frozen,
                oldTokenRel.kycGranted,
                oldTokenRel.destroyed,
                oldTokenRel.notYetPersisted,
                oldTokenRel.automaticAssociation,
                balanceChange);
    }

    /**
     * Creates new instance of {@link TokenRelationship} with updated destroyed field in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldTokenRel
     * @return new instance of {@link TokenRelationship}
     */
    private TokenRelationship createNewDestroyedTokenRelationship(TokenRelationship oldTokenRel) {
        return new TokenRelationship(
                oldTokenRel.token,
                oldTokenRel.account,
                oldTokenRel.balance,
                oldTokenRel.frozen,
                oldTokenRel.kycGranted,
                true,
                oldTokenRel.notYetPersisted,
                oldTokenRel.automaticAssociation,
                balanceChange);
    }

    /**
     * Creates new instance of {@link TokenRelationship} with updated treasury in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldTokenRel
     * @param newAccount
     * @return new instance of {@link TokenRelationship}
     */
    private TokenRelationship createNewTokenRelationshipWithNewTreasuryAccount(
            TokenRelationship oldTokenRel, Account newAccount) {
        return new TokenRelationship(
                oldTokenRel.token,
                newAccount,
                oldTokenRel.balance,
                oldTokenRel.frozen,
                oldTokenRel.kycGranted,
                oldTokenRel.destroyed,
                oldTokenRel.notYetPersisted,
                oldTokenRel.automaticAssociation,
                oldTokenRel.balanceChange);
    }

    /**
     * Creates new instance of {@link TokenRelationship} with updated notYetPersisted field in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldTokenRel
     * @param notYetPersisted
     * @return new instance of {@link TokenRelationship}
     */
    private TokenRelationship createNewPersistedTokenRelationship(
            TokenRelationship oldTokenRel, boolean notYetPersisted) {
        return new TokenRelationship(
                oldTokenRel.token,
                oldTokenRel.account,
                oldTokenRel.balance,
                oldTokenRel.frozen,
                oldTokenRel.kycGranted,
                oldTokenRel.destroyed,
                notYetPersisted,
                oldTokenRel.automaticAssociation,
                oldTokenRel.balanceChange);
    }

    /**
     * Creates new instance of {@link TokenRelationship} with updated notYetPersisted field in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldTokenRel
     * @param newToken
     * @return new instance of {@link TokenRelationship}
     */
    private TokenRelationship createNewTokenRelationshipWithToken(TokenRelationship oldTokenRel, Token newToken) {
        return new TokenRelationship(
                newToken,
                oldTokenRel.account,
                oldTokenRel.balance,
                oldTokenRel.frozen,
                oldTokenRel.kycGranted,
                oldTokenRel.destroyed,
                oldTokenRel.notYetPersisted,
                oldTokenRel.automaticAssociation,
                oldTokenRel.balanceChange);
    }
    /**
     * Creates new instance of {@link TokenRelationship} with updated frozen field in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldTokenRel
     * @param frozen
     * @return new instance of {@link TokenRelationship}
     */
    private TokenRelationship createNewTokenRelationshipWithFrozenFlag(TokenRelationship oldTokenRel, boolean frozen) {
        return new TokenRelationship(
                oldTokenRel.token,
                oldTokenRel.account,
                oldTokenRel.balance,
                frozen,
                oldTokenRel.kycGranted,
                oldTokenRel.destroyed,
                oldTokenRel.notYetPersisted,
                oldTokenRel.automaticAssociation,
                oldTokenRel.balanceChange);
    }

    /**
     * Creates new instance of {@link TokenRelationship} with updated kycGranted field in order to keep the object's
     * immutability and avoid entry points for changing the state.
     *
     * @param oldTokenRel
     * @param kycGranted
     * @return new instance of {@link TokenRelationship}
     */
    private TokenRelationship createNewTokenRelationshipWithNewKycGranted(
            TokenRelationship oldTokenRel, boolean kycGranted) {
        return new TokenRelationship(
                oldTokenRel.token,
                oldTokenRel.account,
                oldTokenRel.balance,
                oldTokenRel.frozen,
                kycGranted,
                oldTokenRel.destroyed,
                oldTokenRel.notYetPersisted,
                oldTokenRel.automaticAssociation,
                oldTokenRel.balanceChange);
    }

    /**
     * Creates new instance of {@link TokenRelationship} with updated automaticAssociation field in order to keep the
     * object's immutability and avoid entry points for changing the state.
     *
     * @param oldTokenRel
     * @param automaticAssociation
     * @return new instance of {@link TokenRelationship}
     */
    private TokenRelationship createNewTokenRelationshipWithNewAutomaticAssociation(
            TokenRelationship oldTokenRel, boolean automaticAssociation) {
        return new TokenRelationship(
                oldTokenRel.token,
                oldTokenRel.account,
                oldTokenRel.balance,
                oldTokenRel.frozen,
                oldTokenRel.kycGranted,
                oldTokenRel.destroyed,
                oldTokenRel.notYetPersisted,
                automaticAssociation,
                oldTokenRel.balanceChange);
    }

    public long getBalance() {
        return balance != null ? balance.get() : 0L;
    }

    /**
     * Update the balance of this relationship token held by the account.
     *
     * <p>This <b>does</b> change the return value of {@link TokenRelationship#getBalanceChange()}.
     *
     * @param balance the updated balance of the relationship
     */
    public TokenRelationship setBalance(long balance) {
        if (!token.isDeleted()) {
            validateTrue(isTokenFrozenFor(), ACCOUNT_FROZEN_FOR_TOKEN);
            validateTrue(isTokenKycGrantedFor(), ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN);
        }

        long newBalanceChange = (balance - getBalance()) + balanceChange;
        return createNewTokenRelationshipWithNewBalance(this, newBalanceChange, balance);
    }

    public boolean isFrozen() {
        return frozen;
    }

    public TokenRelationship setFrozen(boolean frozen) {
        return createNewTokenRelationshipWithFrozenFlag(this, frozen);
    }

    public boolean isKycGranted() {
        return kycGranted;
    }

    public TokenRelationship setKycGranted(boolean kycGranted) {
        return createNewTokenRelationshipWithNewKycGranted(this, kycGranted);
    }

    /**
     * Modifies the state of the KYC property to either true (granted) or false (revoked).
     *
     * <p>Before the property modification, the method performs validation, that the respective
     * token has a KYC key.
     *
     * @param isGranted the new state of the property
     */
    public TokenRelationship changeKycState(boolean isGranted) {
        validateTrue(token.hasKycKey(), TOKEN_HAS_NO_KYC_KEY);
        return createNewTokenRelationshipWithNewKycGranted(this, isGranted);
    }

    public long getBalanceChange() {
        return balanceChange;
    }

    public Token getToken() {
        return token;
    }

    public TokenRelationship setToken(Token newToken) {
        return createNewTokenRelationshipWithToken(this, newToken);
    }

    public Account getAccount() {
        return account;
    }

    public TokenRelationship setAccount(Account newAccount) {
        return createNewTokenRelationshipWithNewTreasuryAccount(this, newAccount);
    }

    boolean hasInvolvedIds(Id tokenId, Id accountId) {
        return account.getId().equals(accountId) && token.getId().equals(tokenId);
    }

    public boolean isNotYetPersisted() {
        return notYetPersisted;
    }

    public TokenRelationship setNotYetPersisted(boolean notYetPersisted) {
        return createNewPersistedTokenRelationship(this, notYetPersisted);
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public TokenRelationship markAsDestroyed() {
        validateFalse(notYetPersisted, FAIL_INVALID);
        return createNewDestroyedTokenRelationship(this);
    }

    public boolean hasChangesForRecord() {
        return balanceChange != 0 && (hasCommonRepresentation() || token.isDeleted());
    }

    public boolean hasCommonRepresentation() {
        return token.getType() == TokenType.FUNGIBLE_COMMON;
    }

    public boolean hasUniqueRepresentation() {
        return token.getType() == TokenType.NON_FUNGIBLE_UNIQUE;
    }

    public boolean isAutomaticAssociation() {
        return automaticAssociation;
    }

    public TokenRelationship setAutomaticAssociation(boolean automaticAssociation) {
        return createNewTokenRelationshipWithNewAutomaticAssociation(this, automaticAssociation);
    }

    private boolean isTokenFrozenFor() {
        return !token.hasFreezeKey() || !frozen;
    }

    private boolean isTokenKycGrantedFor() {
        return !token.hasKycKey() || kycGranted;
    }

    /* The object methods below are only overridden to improve
    readability of unit tests; model objects are not used in hash-based
    collections, so the performance of these methods doesn't matter. */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(TokenRelationship.class)
                .add("notYetPersisted", notYetPersisted)
                .add("account", account)
                .add("token", token)
                .add("balance", getBalance())
                .add("balanceChange", balanceChange)
                .add("frozen", frozen)
                .add("kycGranted", kycGranted)
                .add("isAutomaticAssociation", automaticAssociation)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenRelationship that = (TokenRelationship) o;
        return isFrozen() == that.isFrozen()
                && isKycGranted() == that.isKycGranted()
                && isDestroyed() == that.isDestroyed()
                && isNotYetPersisted() == that.isNotYetPersisted()
                && isAutomaticAssociation() == that.isAutomaticAssociation()
                && getBalanceChange() == that.getBalanceChange()
                && Objects.equals(getToken(), that.getToken())
                && Objects.equals(getAccount(), that.getAccount())
                && Objects.equals(getBalance(), that.getBalance());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getToken(),
                getAccount(),
                getBalance(),
                isFrozen(),
                isKycGranted(),
                isDestroyed(),
                isNotYetPersisted(),
                isAutomaticAssociation(),
                getBalanceChange());
    }
}
