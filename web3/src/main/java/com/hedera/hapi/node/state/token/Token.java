// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.token;

import static java.util.Objects.requireNonNull;
import static org.hiero.mirror.web3.utils.Suppliers.areSuppliersEqual;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.JsonCodec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Representation of a Hedera Token Service token entity in the network Merkle tree.
 * <p>
 * As with all network entities, a token has a unique entity number, which is usually given along
 * with the network's shard and realm in the form of a shard.realm.number id.
 *
 * @param tokenId <b>(1)</b> The unique entity id of this token.
 * @param name <b>(2)</b> The human-readable name of this token. Need not be unique. Maximum length allowed is 100 bytes.
 * @param symbol <b>(3)</b> The human-readable symbol for the token. It is not necessarily unique. Maximum length allowed is 100 bytes.
 * @param decimals <b>(4)</b> The number of decimal places of this token. If decimals are 8 or 11, then the number of whole
 *                 tokens can be at most a few billions or millions, respectively. For example, it could match
 *                 Bitcoin (21 million whole tokens with 8 decimals) or hbars (50 billion whole tokens with 8 decimals).
 *                 It could even match Bitcoin with milli-satoshis (21 million whole tokens with 11 decimals).
 * @param totalSupplySupplier <b>(5)</b> The total supply of this token wrapped in a Supplier.
 * @param treasuryAccountId <b>(6)</b> The treasury account id of this token.
 * @param adminKey <b>(7)</b> (Optional) The admin key of this token. If this key is set, the token is mutable.
 *                 A mutable token can be modified.
 *                 If this key is not set on token creation, it cannot be modified.
 * @param kycKey <b>(8)</b> (Optional) The kyc key of this token.
 *               If this key is not set on token creation, it can only be set if the token has admin key set.
 * @param freezeKey <b>(9)</b> (Optional) The freeze key of this token. This key is needed for freezing the token.
 *                  If this key is not set on token creation, it can only be set if the token has admin key set.
 * @param wipeKey <b>(10)</b> (Optional) The wipe key of this token. This key is needed for wiping the token.
 *                If this key is not set on token creation, it can only be set if the token has admin key set.
 * @param supplyKey <b>(11)</b> (Optional) The supply key of this token. This key is needed for minting or burning token.
 *                  If this key is not set on token creation, it can only be set if the token has admin key set.
 * @param feeScheduleKey <b>(12)</b> (Optional) The fee schedule key of this token. This key should be set, in order to make any
 *                       changes to the custom fee schedule.
 *                       If this key is not set on token creation, it can only be set if the token has admin key set.
 * @param pauseKey <b>(13)</b> (Optional) The pause key of this token. This key is needed for pausing the token.
 *                 If this key is not set on token creation, it can only be set if the token has admin key set.
 * @param lastUsedSerialNumber <b>(14)</b> The last used serial number of this token.
 * @param deleted <b>(15)</b> The flag indicating if this token is deleted.
 * @param tokenType <b>(16)</b> The type of this token. A token can be either FUNGIBLE_COMMON or NON_FUNGIBLE_UNIQUE.
 *                  If it has been omitted during token creation, FUNGIBLE_COMMON type is used.
 * @param supplyType <b>(17)</b> The supply type of this token.A token can have either INFINITE or FINITE supply type.
 *                   If it has been omitted during token creation, INFINITE type is used.
 * @param autoRenewAccountId <b>(18)</b> The id of the account (if any) that the network will attempt to charge for the
 *  *                           token's auto-renewal upon expiration.
 * @param autoRenewSeconds <b>(19)</b> The number of seconds the network should automatically extend the token's expiration by, if the
 *                         token has a valid auto-renew account, and is not deleted upon expiration.
 *                         If this is not provided in a allowed range on token creation, the transaction will fail with INVALID_AUTO_RENEWAL_PERIOD.
 *                         The default values for the minimum period and maximum period are 30 days and 90 days, respectively.
 * @param expirationSecond <b>(20)</b> The expiration time of the token, in seconds since the epoch.
 * @param memo <b>(21)</b> An optional description of the token with UTF-8 encoding up to 100 bytes.
 * @param maxSupply <b>(22)</b> The maximum supply of this token.
 * @param paused <b>(23)</b> The flag indicating if this token is paused.
 * @param accountsFrozenByDefault <b>(24)</b> The flag indicating if this token has accounts associated to it that are frozen by default.
 * @param accountsKycGrantedByDefault <b>(25)</b> The flag indicating if this token has accounts associated with it that are KYC granted by default.
 * @param customFeesSupplier <b>(26)</b> (Optional) The custom fees of this token wrapped in a Supplier.
 * @param metadata <b>(27)</b> Metadata of the created token definition
 * @param metadataKey <b>(28)</b> The key which can change the metadata of a token
 *                    (token definition and individual NFTs).
 */
public record Token(
        @jakarta.annotation.Nullable TokenID tokenId,
        @Nonnull String name,
        @Nonnull String symbol,
        int decimals,
        Supplier<Long> totalSupplySupplier,
        @Nullable AccountID treasuryAccountId,
        @Nullable Key adminKey,
        @Nullable Key kycKey,
        @Nullable Key freezeKey,
        @Nullable Key wipeKey,
        @Nullable Key supplyKey,
        @Nullable Key feeScheduleKey,
        @Nullable Key pauseKey,
        long lastUsedSerialNumber,
        boolean deleted,
        TokenType tokenType,
        TokenSupplyType supplyType,
        @Nullable AccountID autoRenewAccountId,
        long autoRenewSeconds,
        long expirationSecond,
        @Nonnull String memo,
        long maxSupply,
        boolean paused,
        boolean accountsFrozenByDefault,
        boolean accountsKycGrantedByDefault,
        @Nonnull Supplier<List<CustomFee>> customFeesSupplier,
        @Nonnull Bytes metadata,
        @Nullable Key metadataKey) {
    /** Protobuf codec for reading and writing in protobuf format */
    public static final Codec<Token> PROTOBUF = new com.hedera.hapi.node.state.token.codec.TokenProtoCodec();
    /** JSON codec for reading and writing in JSON format */
    public static final JsonCodec<Token> JSON = new com.hedera.hapi.node.state.token.codec.TokenJsonCodec();

    /** Default instance with all fields set to default values */
    public static final Token DEFAULT = newBuilder()
            .totalSupply(0L)
            .autoRenewAccountId(null)
            .treasuryAccountId(null)
            .build();
    /**
     * Create a pre-populated Token.
     *
     * @param tokenId <b>(1)</b> The unique entity id of this token.
     * @param name <b>(2)</b> The human-readable name of this token. Need not be unique. Maximum length allowed is 100 bytes.
     * @param symbol <b>(3)</b> The human-readable symbol for the token. It is not necessarily unique. Maximum length allowed is 100 bytes.
     * @param decimals <b>(4)</b> The number of decimal places of this token. If decimals are 8 or 11, then the number of whole
     *                 tokens can be at most a few billions or millions, respectively. For example, it could match
     *                 Bitcoin (21 million whole tokens with 8 decimals) or hbars (50 billion whole tokens with 8 decimals).
     *                 It could even match Bitcoin with milli-satoshis (21 million whole tokens with 11 decimals).
     * @param totalSupply <b>(5)</b> The total supply of this token wrapped in a Supplier.
     * @param treasuryAccountId <b>(6)</b> The treasury account id of this token wrapped in a Supplier.
     * @param adminKey <b>(7)</b> (Optional) The admin key of this token. If this key is set, the token is mutable.
     *                 A mutable token can be modified.
     *                 If this key is not set on token creation, it cannot be modified.
     * @param kycKey <b>(8)</b> (Optional) The kyc key of this token.
     *               If this key is not set on token creation, it can only be set if the token has admin key set.
     * @param freezeKey <b>(9)</b> (Optional) The freeze key of this token. This key is needed for freezing the token.
     *                  If this key is not set on token creation, it can only be set if the token has admin key set.
     * @param wipeKey <b>(10)</b> (Optional) The wipe key of this token. This key is needed for wiping the token.
     *                If this key is not set on token creation, it can only be set if the token has admin key set.
     * @param supplyKey <b>(11)</b> (Optional) The supply key of this token. This key is needed for minting or burning token.
     *                  If this key is not set on token creation, it can only be set if the token has admin key set.
     * @param feeScheduleKey <b>(12)</b> (Optional) The fee schedule key of this token. This key should be set, in order to make any
     *                       changes to the custom fee schedule.
     *                       If this key is not set on token creation, it can only be set if the token has admin key set.
     * @param pauseKey <b>(13)</b> (Optional) The pause key of this token. This key is needed for pausing the token.
     *                 If this key is not set on token creation, it can only be set if the token has admin key set.
     * @param lastUsedSerialNumber <b>(14)</b> The last used serial number of this token.
     * @param deleted <b>(15)</b> The flag indicating if this token is deleted.
     * @param tokenType <b>(16)</b> The type of this token. A token can be either FUNGIBLE_COMMON or NON_FUNGIBLE_UNIQUE.
     *                  If it has been omitted during token creation, FUNGIBLE_COMMON type is used.
     * @param supplyType <b>(17)</b> The supply type of this token.A token can have either INFINITE or FINITE supply type.
     *                   If it has been omitted during token creation, INFINITE type is used.
     * @param autoRenewAccountId <b>(18)</b> The id of the account (if any) that the network will attempt to charge for the
     *  *                           token's auto-renewal upon expiration wrapped in a Supplier.
     * @param autoRenewSeconds <b>(19)</b> The number of seconds the network should automatically extend the token's expiration by, if the
     *                         token has a valid auto-renew account, and is not deleted upon expiration.
     *                         If this is not provided in a allowed range on token creation, the transaction will fail with INVALID_AUTO_RENEWAL_PERIOD.
     *                         The default values for the minimum period and maximum period are 30 days and 90 days, respectively.
     * @param expirationSecond <b>(20)</b> The expiration time of the token, in seconds since the epoch.
     * @param memo <b>(21)</b> An optional description of the token with UTF-8 encoding up to 100 bytes.
     * @param maxSupply <b>(22)</b> The maximum supply of this token.
     * @param paused <b>(23)</b> The flag indicating if this token is paused.
     * @param accountsFrozenByDefault <b>(24)</b> The flag indicating if this token has accounts associated to it that are frozen by default.
     * @param accountsKycGrantedByDefault <b>(25)</b> The flag indicating if this token has accounts associated with it that are KYC granted by default.
     * @param customFees <b>(26)</b> (Optional) The custom fees of this token wrapped in a Supplier.
     * @param metadata <b>(27)</b> Metadata of the created token definition
     * @param metadataKey <b>(28)</b> The key which can change the metadata of a token
     *                    (token definition and individual NFTs).
     */
    public Token(
            TokenID tokenId,
            String name,
            String symbol,
            int decimals,
            long totalSupply,
            AccountID treasuryAccountId,
            Key adminKey,
            Key kycKey,
            Key freezeKey,
            Key wipeKey,
            Key supplyKey,
            Key feeScheduleKey,
            Key pauseKey,
            long lastUsedSerialNumber,
            boolean deleted,
            TokenType tokenType,
            TokenSupplyType supplyType,
            AccountID autoRenewAccountId,
            long autoRenewSeconds,
            long expirationSecond,
            String memo,
            long maxSupply,
            boolean paused,
            boolean accountsFrozenByDefault,
            boolean accountsKycGrantedByDefault,
            List<CustomFee> customFees,
            Bytes metadata,
            Key metadataKey) {
        this(
                tokenId,
                name,
                symbol,
                decimals,
                () -> totalSupply,
                treasuryAccountId,
                adminKey,
                kycKey,
                freezeKey,
                wipeKey,
                supplyKey,
                feeScheduleKey,
                pauseKey,
                lastUsedSerialNumber,
                deleted,
                tokenType,
                supplyType,
                autoRenewAccountId,
                autoRenewSeconds,
                expirationSecond,
                memo,
                maxSupply,
                paused,
                accountsFrozenByDefault,
                accountsKycGrantedByDefault,
                () -> customFees,
                metadata,
                metadataKey);
    }

    /**
     * Return a new builder for building a model object. This is just a shortcut for <code>new Model.Builder()</code>.
     *
     * @return a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Override the default hashCode method for
     * all other objects to make hashCode
     */
    @Override
    public int hashCode() {
        int result = 1;
        if (tokenId != null && !tokenId.equals(DEFAULT.tokenId)) {
            result = 31 * result + tokenId.hashCode();
        }
        if (name != null && !name.equals(DEFAULT.name)) {
            result = 31 * result + name.hashCode();
        }
        if (symbol != null && !symbol.equals(DEFAULT.symbol)) {
            result = 31 * result + symbol.hashCode();
        }
        if (decimals != DEFAULT.decimals) {
            result = 31 * result + Integer.hashCode(decimals);
        }
        if (totalSupplySupplier != null) {
            Long currentValue = totalSupplySupplier.get();
            Long defaultValue = (DEFAULT.totalSupplySupplier != null) ? DEFAULT.totalSupplySupplier.get() : null;

            if (currentValue != null && !currentValue.equals(defaultValue)) {
                result = 31 * result + Long.hashCode(currentValue);
            }
        }
        if (treasuryAccountId != null && !treasuryAccountId.equals(DEFAULT.treasuryAccountId)) {
            result = 31 * result + treasuryAccountId.hashCode();
        }
        if (adminKey != null && !adminKey.equals(DEFAULT.adminKey)) {
            result = 31 * result + adminKey.hashCode();
        }
        if (kycKey != null && !kycKey.equals(DEFAULT.kycKey)) {
            result = 31 * result + kycKey.hashCode();
        }
        if (freezeKey != null && !freezeKey.equals(DEFAULT.freezeKey)) {
            result = 31 * result + freezeKey.hashCode();
        }
        if (wipeKey != null && !wipeKey.equals(DEFAULT.wipeKey)) {
            result = 31 * result + wipeKey.hashCode();
        }
        if (supplyKey != null && !supplyKey.equals(DEFAULT.supplyKey)) {
            result = 31 * result + supplyKey.hashCode();
        }
        if (feeScheduleKey != null && !feeScheduleKey.equals(DEFAULT.feeScheduleKey)) {
            result = 31 * result + feeScheduleKey.hashCode();
        }
        if (pauseKey != null && !pauseKey.equals(DEFAULT.pauseKey)) {
            result = 31 * result + pauseKey.hashCode();
        }
        if (lastUsedSerialNumber != DEFAULT.lastUsedSerialNumber) {
            result = 31 * result + Long.hashCode(lastUsedSerialNumber);
        }
        if (deleted != DEFAULT.deleted) {
            result = 31 * result + Boolean.hashCode(deleted);
        }
        if (tokenType != null && !tokenType.equals(DEFAULT.tokenType)) {
            result = 31 * result + Integer.hashCode(tokenType.protoOrdinal());
        }
        if (supplyType != null && !supplyType.equals(DEFAULT.supplyType)) {
            result = 31 * result + Integer.hashCode(supplyType.protoOrdinal());
        }
        if (autoRenewAccountId != null && !autoRenewAccountId.equals(DEFAULT.autoRenewAccountId)) {
            result = 31 * result + autoRenewAccountId.hashCode();
        }
        if (autoRenewSeconds != DEFAULT.autoRenewSeconds) {
            result = 31 * result + Long.hashCode(autoRenewSeconds);
        }
        if (expirationSecond != DEFAULT.expirationSecond) {
            result = 31 * result + Long.hashCode(expirationSecond);
        }
        if (memo != null && !memo.equals(DEFAULT.memo)) {
            result = 31 * result + memo.hashCode();
        }
        if (maxSupply != DEFAULT.maxSupply) {
            result = 31 * result + Long.hashCode(maxSupply);
        }
        if (paused != DEFAULT.paused) {
            result = 31 * result + Boolean.hashCode(paused);
        }
        if (accountsFrozenByDefault != DEFAULT.accountsFrozenByDefault) {
            result = 31 * result + Boolean.hashCode(accountsFrozenByDefault);
        }
        if (accountsKycGrantedByDefault != DEFAULT.accountsKycGrantedByDefault) {
            result = 31 * result + Boolean.hashCode(accountsKycGrantedByDefault);
        }
        for (Object o : customFeesSupplier.get()) {
            if (o != null) {
                result = 31 * result + o.hashCode();
            } else {
                result = 31 * result;
            }
        }
        if (metadata != null && !metadata.equals(DEFAULT.metadata)) {
            result = 31 * result + metadata.hashCode();
        }
        if (metadataKey != null && !metadataKey.equals(DEFAULT.metadataKey)) {
            result = 31 * result + metadataKey.hashCode();
        }
        long hashCode = result;
        // Shifts: 30, 27, 16, 20, 5, 18, 10, 24, 30
        hashCode += hashCode << 30;
        hashCode ^= hashCode >>> 27;
        hashCode += hashCode << 16;
        hashCode ^= hashCode >>> 20;
        hashCode += hashCode << 5;
        hashCode ^= hashCode >>> 18;
        hashCode += hashCode << 10;
        hashCode ^= hashCode >>> 24;
        hashCode += hashCode << 30;

        return (int) hashCode;
    }

    /**
     * Override the default equals method for
     */
    @Override
    public boolean equals(Object that) {
        if (that == null || this.getClass() != that.getClass()) {
            return false;
        }
        Token thatObj = (Token) that;
        if (tokenId == null && thatObj.tokenId != null) {
            return false;
        }
        if (tokenId != null && !tokenId.equals(thatObj.tokenId)) {
            return false;
        }
        if (name == null && thatObj.name != null) {
            return false;
        }
        if (name != null && !name.equals(thatObj.name)) {
            return false;
        }
        if (symbol == null && thatObj.symbol != null) {
            return false;
        }
        if (symbol != null && !symbol.equals(thatObj.symbol)) {
            return false;
        }
        if (decimals != thatObj.decimals) {
            return false;
        }
        if (!areSuppliersEqual(totalSupplySupplier, thatObj.totalSupplySupplier)) {
            return false;
        }
        if (treasuryAccountId == null && thatObj.treasuryAccountId != null) {
            return false;
        }
        if (treasuryAccountId != null && !treasuryAccountId.equals(thatObj.treasuryAccountId)) {
            return false;
        }
        if (adminKey == null && thatObj.adminKey != null) {
            return false;
        }
        if (adminKey != null && !adminKey.equals(thatObj.adminKey)) {
            return false;
        }
        if (kycKey == null && thatObj.kycKey != null) {
            return false;
        }
        if (kycKey != null && !kycKey.equals(thatObj.kycKey)) {
            return false;
        }
        if (freezeKey == null && thatObj.freezeKey != null) {
            return false;
        }
        if (freezeKey != null && !freezeKey.equals(thatObj.freezeKey)) {
            return false;
        }
        if (wipeKey == null && thatObj.wipeKey != null) {
            return false;
        }
        if (wipeKey != null && !wipeKey.equals(thatObj.wipeKey)) {
            return false;
        }
        if (supplyKey == null && thatObj.supplyKey != null) {
            return false;
        }
        if (supplyKey != null && !supplyKey.equals(thatObj.supplyKey)) {
            return false;
        }
        if (feeScheduleKey == null && thatObj.feeScheduleKey != null) {
            return false;
        }
        if (feeScheduleKey != null && !feeScheduleKey.equals(thatObj.feeScheduleKey)) {
            return false;
        }
        if (pauseKey == null && thatObj.pauseKey != null) {
            return false;
        }
        if (pauseKey != null && !pauseKey.equals(thatObj.pauseKey)) {
            return false;
        }
        if (lastUsedSerialNumber != thatObj.lastUsedSerialNumber) {
            return false;
        }
        if (deleted != thatObj.deleted) {
            return false;
        }
        if (tokenType == null && thatObj.tokenType != null) {
            return false;
        }
        if (tokenType != null && !tokenType.equals(thatObj.tokenType)) {
            return false;
        }
        if (supplyType == null && thatObj.supplyType != null) {
            return false;
        }
        if (supplyType != null && !supplyType.equals(thatObj.supplyType)) {
            return false;
        }
        if (autoRenewAccountId == null && thatObj.autoRenewAccountId != null) {
            return false;
        }
        if (autoRenewAccountId != null && !autoRenewAccountId.equals(thatObj.autoRenewAccountId)) {
            return false;
        }
        if (autoRenewSeconds != thatObj.autoRenewSeconds) {
            return false;
        }
        if (expirationSecond != thatObj.expirationSecond) {
            return false;
        }
        if (memo == null && thatObj.memo != null) {
            return false;
        }
        if (memo != null && !memo.equals(thatObj.memo)) {
            return false;
        }
        if (maxSupply != thatObj.maxSupply) {
            return false;
        }
        if (paused != thatObj.paused) {
            return false;
        }
        if (accountsFrozenByDefault != thatObj.accountsFrozenByDefault) {
            return false;
        }
        if (accountsKycGrantedByDefault != thatObj.accountsKycGrantedByDefault) {
            return false;
        }

        if (!customFeesSupplier.get().equals(thatObj.customFeesSupplier.get())) {
            return false;
        }

        if (metadata == null && thatObj.metadata != null) {
            return false;
        }
        if (metadata != null && !metadata.equals(thatObj.metadata)) {
            return false;
        }
        if (metadataKey == null && thatObj.metadataKey != null) {
            return false;
        }
        return metadataKey == null || metadataKey.equals(thatObj.metadataKey);
    }
    /**
     * Convenience method to check if the tokenId has a value
     *
     * @return true of the tokenId has a value
     */
    public boolean hasTokenId() {
        return tokenId != null;
    }

    /**
     * Gets the value for tokenId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if tokenId is null
     * @return the value for tokenId if it has a value, or else returns the default value
     */
    public TokenID tokenIdOrElse(@Nonnull final TokenID defaultValue) {
        return hasTokenId() ? tokenId : defaultValue;
    }

    /**
     * Gets the value for tokenId if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for tokenId if it has a value
     * @throws NullPointerException if tokenId is null
     */
    public @Nonnull TokenID tokenIdOrThrow() {
        return requireNonNull(tokenId, "Field tokenId is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the tokenId has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifTokenId(@Nonnull final Consumer<TokenID> ifPresent) {
        if (hasTokenId()) {
            ifPresent.accept(tokenId);
        }
    }

    /**
     * Convenience method to check if the treasuryAccountId has a value
     *
     * @return true of the treasuryAccountId has a value
     */
    public boolean hasTreasuryAccountId() {
        return treasuryAccountId != null;
    }

    /**
     * Gets the value for treasuryAccountId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if treasuryAccountId is null
     * @return the value for treasuryAccountId if it has a value, or else returns the default value
     */
    public AccountID treasuryAccountIdOrElse(@Nonnull final AccountID defaultValue) {
        return hasTreasuryAccountId() ? treasuryAccountId : defaultValue;
    }

    /**
     * Gets the value for treasuryAccountId if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for treasuryAccountId if it has a value
     * @throws NullPointerException if treasuryAccountId is null
     */
    public @Nonnull AccountID treasuryAccountIdOrThrow() {
        return requireNonNull(treasuryAccountId, "Field treasuryAccountId is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the treasuryAccountId has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifTreasuryAccountId(@Nonnull final Consumer<AccountID> ifPresent) {
        if (hasTreasuryAccountId()) {
            ifPresent.accept(treasuryAccountId);
        }
    }

    /**
     * Convenience method to check if the adminKey has a value
     *
     * @return true of the adminKey has a value
     */
    public boolean hasAdminKey() {
        return adminKey != null;
    }

    /**
     * Gets the value for adminKey if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if adminKey is null
     * @return the value for adminKey if it has a value, or else returns the default value
     */
    public Key adminKeyOrElse(@Nonnull final Key defaultValue) {
        return hasAdminKey() ? adminKey : defaultValue;
    }

    /**
     * Gets the value for adminKey if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for adminKey if it has a value
     * @throws NullPointerException if adminKey is null
     */
    public @Nonnull Key adminKeyOrThrow() {
        return requireNonNull(adminKey, "Field adminKey is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the adminKey has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifAdminKey(@Nonnull final Consumer<Key> ifPresent) {
        if (hasAdminKey()) {
            ifPresent.accept(adminKey);
        }
    }

    /**
     * Convenience method to check if the kycKey has a value
     *
     * @return true of the kycKey has a value
     */
    public boolean hasKycKey() {
        return kycKey != null;
    }

    /**
     * Gets the value for kycKey if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if kycKey is null
     * @return the value for kycKey if it has a value, or else returns the default value
     */
    public Key kycKeyOrElse(@Nonnull final Key defaultValue) {
        return hasKycKey() ? kycKey : defaultValue;
    }

    /**
     * Gets the value for kycKey if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for kycKey if it has a value
     * @throws NullPointerException if kycKey is null
     */
    public @Nonnull Key kycKeyOrThrow() {
        return requireNonNull(kycKey, "Field kycKey is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the kycKey has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifKycKey(@Nonnull final Consumer<Key> ifPresent) {
        if (hasKycKey()) {
            ifPresent.accept(kycKey);
        }
    }

    /**
     * Convenience method to check if the freezeKey has a value
     *
     * @return true of the freezeKey has a value
     */
    public boolean hasFreezeKey() {
        return freezeKey != null;
    }

    /**
     * Gets the value for freezeKey if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if freezeKey is null
     * @return the value for freezeKey if it has a value, or else returns the default value
     */
    public Key freezeKeyOrElse(@Nonnull final Key defaultValue) {
        return hasFreezeKey() ? freezeKey : defaultValue;
    }

    /**
     * Gets the value for freezeKey if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for freezeKey if it has a value
     * @throws NullPointerException if freezeKey is null
     */
    public @Nonnull Key freezeKeyOrThrow() {
        return requireNonNull(freezeKey, "Field freezeKey is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the freezeKey has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifFreezeKey(@Nonnull final Consumer<Key> ifPresent) {
        if (hasFreezeKey()) {
            ifPresent.accept(freezeKey);
        }
    }

    /**
     * Convenience method to check if the wipeKey has a value
     *
     * @return true of the wipeKey has a value
     */
    public boolean hasWipeKey() {
        return wipeKey != null;
    }

    /**
     * Gets the value for wipeKey if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if wipeKey is null
     * @return the value for wipeKey if it has a value, or else returns the default value
     */
    public Key wipeKeyOrElse(@Nonnull final Key defaultValue) {
        return hasWipeKey() ? wipeKey : defaultValue;
    }

    /**
     * Gets the value for wipeKey if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for wipeKey if it has a value
     * @throws NullPointerException if wipeKey is null
     */
    public @Nonnull Key wipeKeyOrThrow() {
        return requireNonNull(wipeKey, "Field wipeKey is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the wipeKey has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifWipeKey(@Nonnull final Consumer<Key> ifPresent) {
        if (hasWipeKey()) {
            ifPresent.accept(wipeKey);
        }
    }

    /**
     * Convenience method to check if the supplyKey has a value
     *
     * @return true of the supplyKey has a value
     */
    public boolean hasSupplyKey() {
        return supplyKey != null;
    }

    /**
     * Gets the value for supplyKey if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if supplyKey is null
     * @return the value for supplyKey if it has a value, or else returns the default value
     */
    public Key supplyKeyOrElse(@Nonnull final Key defaultValue) {
        return hasSupplyKey() ? supplyKey : defaultValue;
    }

    /**
     * Gets the value for supplyKey if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for supplyKey if it has a value
     * @throws NullPointerException if supplyKey is null
     */
    public @Nonnull Key supplyKeyOrThrow() {
        return requireNonNull(supplyKey, "Field supplyKey is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the supplyKey has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifSupplyKey(@Nonnull final Consumer<Key> ifPresent) {
        if (hasSupplyKey()) {
            ifPresent.accept(supplyKey);
        }
    }

    /**
     * Convenience method to check if the feeScheduleKey has a value
     *
     * @return true of the feeScheduleKey has a value
     */
    public boolean hasFeeScheduleKey() {
        return feeScheduleKey != null;
    }

    /**
     * Gets the value for feeScheduleKey if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if feeScheduleKey is null
     * @return the value for feeScheduleKey if it has a value, or else returns the default value
     */
    public Key feeScheduleKeyOrElse(@Nonnull final Key defaultValue) {
        return hasFeeScheduleKey() ? feeScheduleKey : defaultValue;
    }

    /**
     * Gets the value for feeScheduleKey if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for feeScheduleKey if it has a value
     * @throws NullPointerException if feeScheduleKey is null
     */
    public @Nonnull Key feeScheduleKeyOrThrow() {
        return requireNonNull(feeScheduleKey, "Field feeScheduleKey is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the feeScheduleKey has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifFeeScheduleKey(@Nonnull final Consumer<Key> ifPresent) {
        if (hasFeeScheduleKey()) {
            ifPresent.accept(feeScheduleKey);
        }
    }

    /**
     * Convenience method to check if the pauseKey has a value
     *
     * @return true of the pauseKey has a value
     */
    public boolean hasPauseKey() {
        return pauseKey != null;
    }

    /**
     * Gets the value for pauseKey if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if pauseKey is null
     * @return the value for pauseKey if it has a value, or else returns the default value
     */
    public Key pauseKeyOrElse(@Nonnull final Key defaultValue) {
        return hasPauseKey() ? pauseKey : defaultValue;
    }

    /**
     * Gets the value for pauseKey if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for pauseKey if it has a value
     * @throws NullPointerException if pauseKey is null
     */
    public @Nonnull Key pauseKeyOrThrow() {
        return requireNonNull(pauseKey, "Field pauseKey is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the pauseKey has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifPauseKey(@Nonnull final Consumer<Key> ifPresent) {
        if (hasPauseKey()) {
            ifPresent.accept(pauseKey);
        }
    }

    /**
     * Convenience method to check if the autoRenewAccountId has a value
     *
     * @return true of the autoRenewAccountId has a value
     */
    public boolean hasAutoRenewAccountId() {
        return autoRenewAccountId != null;
    }

    /**
     * Gets the value for autoRenewAccountId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if autoRenewAccountId is null
     * @return the value for autoRenewAccountId if it has a value, or else returns the default value
     */
    public AccountID autoRenewAccountIdOrElse(@Nonnull final AccountID defaultValue) {
        return hasAutoRenewAccountId() ? autoRenewAccountId : defaultValue;
    }

    /**
     * Gets the value for autoRenewAccountId if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for autoRenewAccountId if it has a value
     * @throws NullPointerException if autoRenewAccountId is null
     */
    public @Nonnull AccountID autoRenewAccountIdOrThrow() {
        return requireNonNull(autoRenewAccountId, "Field autoRenewAccountId is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the autoRenewAccountId has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifAutoRenewAccountId(@Nonnull final Consumer<AccountID> ifPresent) {
        if (hasAutoRenewAccountId()) {
            ifPresent.accept(autoRenewAccountId);
        }
    }

    /**
     * Convenience method to check if the metadataKey has a value
     *
     * @return true of the metadataKey has a value
     */
    public boolean hasMetadataKey() {
        return metadataKey != null;
    }

    /**
     * Gets the value for metadataKey if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if metadataKey is null
     * @return the value for metadataKey if it has a value, or else returns the default value
     */
    public Key metadataKeyOrElse(@Nonnull final Key defaultValue) {
        return hasMetadataKey() ? metadataKey : defaultValue;
    }

    /**
     * Gets the value for metadataKey if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for metadataKey if it has a value
     * @throws NullPointerException if metadataKey is null
     */
    public @Nonnull Key metadataKeyOrThrow() {
        return requireNonNull(metadataKey, "Field metadataKey is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the metadataKey has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifMetadataKey(@Nonnull final Consumer<Key> ifPresent) {
        if (hasMetadataKey()) {
            ifPresent.accept(metadataKey);
        }
    }

    /**
     * @return The custom fees of this token
     */
    public List<CustomFee> customFees() {
        return customFeesSupplier.get();
    }

    /**
     * @return The total supply of this token
     */
    public long totalSupply() {
        return totalSupplySupplier.get();
    }

    /**
     * Return a builder for building a copy of this model object. It will be pre-populated with all the data from this
     * model object.
     *
     * @return a pre-populated builder
     */
    public Builder copyBuilder() {
        return new Builder(
                tokenId,
                name,
                symbol,
                decimals,
                totalSupplySupplier,
                treasuryAccountId,
                adminKey,
                kycKey,
                freezeKey,
                wipeKey,
                supplyKey,
                feeScheduleKey,
                pauseKey,
                lastUsedSerialNumber,
                deleted,
                tokenType,
                supplyType,
                autoRenewAccountId,
                autoRenewSeconds,
                expirationSecond,
                memo,
                maxSupply,
                paused,
                accountsFrozenByDefault,
                accountsKycGrantedByDefault,
                customFeesSupplier,
                metadata,
                metadataKey);
    }

    /**
     * Builder class for easy creation, ideal for clean code where performance is not critical. In critical performance
     * paths use the constructor directly.
     */
    public static final class Builder {
        @Nullable
        private TokenID tokenId = null;

        @Nonnull
        private String name = "";

        @Nonnull
        private String symbol = "";

        private int decimals = 0;

        @Nullable
        private Supplier<Long> totalSupplySupplier = null;

        @Nullable
        private AccountID treasuryAccountId = null;

        @Nullable
        private Key adminKey = null;

        @Nullable
        private Key kycKey = null;

        @Nullable
        private Key freezeKey = null;

        @Nullable
        private Key wipeKey = null;

        @Nullable
        private Key supplyKey = null;

        @Nullable
        private Key feeScheduleKey = null;

        @Nullable
        private Key pauseKey = null;

        private long lastUsedSerialNumber = 0;
        private boolean deleted = false;
        private TokenType tokenType = TokenType.fromProtobufOrdinal(0);
        private TokenSupplyType supplyType = TokenSupplyType.fromProtobufOrdinal(0);

        @Nullable
        private AccountID autoRenewAccountId = null;

        private long autoRenewSeconds = 0;
        private long expirationSecond = 0;

        @Nonnull
        private String memo = "";

        private long maxSupply = 0;
        private boolean paused = false;
        private boolean accountsFrozenByDefault = false;
        private boolean accountsKycGrantedByDefault = false;

        @Nullable
        private Supplier<List<CustomFee>> customFeesSupplier = Collections::emptyList;

        @Nonnull
        private Bytes metadata = Bytes.EMPTY;

        @Nullable
        private Key metadataKey = null;

        /**
         * Create an empty builder
         */
        public Builder() {}

        /**
         * Create a pre-populated Builder.
         *
         * @param tokenId                     <b>(1)</b> The unique entity id of this token.
         * @param name                        <b>(2)</b> The human-readable name of this token. Need not be unique.
         *                                    Maximum length allowed is 100 bytes.
         * @param symbol                      <b>(3)</b> The human-readable symbol for the token. It is not necessarily
         *                                    unique. Maximum length allowed is 100 bytes.
         * @param decimals                    <b>(4)</b> The number of decimal places of this token. If decimals are 8
         *                                    or 11, then the number of whole
         *                                    tokens can be at most a few billions or millions, respectively. For
         *                                    example, it could match Bitcoin (21 million whole tokens with 8 decimals)
         *                                    or hbars (50 billion whole tokens with 8 decimals). It could even match
         *                                    Bitcoin with milli-satoshis (21 million whole tokens with 11 decimals).
         * @param totalSupplySupplier         <b>(5)</b> The total supply of this token wrapped in a Supplier.
         * @param treasuryAccountId           <b>(6)</b> The treasury account id of this token.
         * @param adminKey                    <b>(7)</b> (Optional) The admin key of this token. If this key is set, the
         *                                    token is mutable.
         *                                    A mutable token can be modified. If this key is not set on token creation,
         *                                    it cannot be modified.
         * @param kycKey                      <b>(8)</b> (Optional) The kyc key of this token.
         *                                    If this key is not set on token creation, it can only be set if the token
         *                                    has admin key set.
         * @param freezeKey                   <b>(9)</b> (Optional) The freeze key of this token. This key is needed for
         *                                    freezing the token.
         *                                    If this key is not set on token creation, it can only be set if the token
         *                                    has admin key set.
         * @param wipeKey                     <b>(10)</b> (Optional) The wipe key of this token. This key is needed for
         *                                    wiping the token.
         *                                    If this key is not set on token creation, it can only be set if the token
         *                                    has admin key set.
         * @param supplyKey                   <b>(11)</b> (Optional) The supply key of this token. This key is needed
         *                                    for minting or burning token.
         *                                    If this key is not set on token creation, it can only be set if the token
         *                                    has admin key set.
         * @param feeScheduleKey              <b>(12)</b> (Optional) The fee schedule key of this token. This key should
         *                                    be set, in order to make any
         *                                    changes to the custom fee schedule. If this key is not set on token
         *                                    creation, it can only be set if the token has admin key set.
         * @param pauseKey                    <b>(13)</b> (Optional) The pause key of this token. This key is needed for
         *                                    pausing the token.
         *                                    If this key is not set on token creation, it can only be set if the token
         *                                    has admin key set.
         * @param lastUsedSerialNumber        <b>(14)</b> The last used serial number of this token.
         * @param deleted                     <b>(15)</b> The flag indicating if this token is deleted.
         * @param tokenType                   <b>(16)</b> The type of this token. A token can be either FUNGIBLE_COMMON
         *                                    or NON_FUNGIBLE_UNIQUE.
         *                                    If it has been omitted during token creation, FUNGIBLE_COMMON type is
         *                                    used.
         * @param supplyType                  <b>(17)</b> The supply type of this token.A token can have either INFINITE
         *                                    or FINITE supply type.
         *                                    If it has been omitted during token creation, INFINITE type is used.
         * @param autoRenewAccountId          <b>(18)</b> The id of the account (if any) that the network will attempt
         *                                    to charge for the
         *                                    *                           token's auto-renewal upon expiration.
         * @param autoRenewSeconds            <b>(19)</b> The number of seconds the network should automatically extend
         *                                    the token's expiration by, if the
         *                                    token has a valid auto-renew account, and is not deleted upon expiration.
         *                                    If this is not provided in a allowed range on token creation, the
         *                                    transaction will fail with INVALID_AUTO_RENEWAL_PERIOD. The default values
         *                                    for the minimum period and maximum period are 30 days and 90 days,
         *                                    respectively.
         * @param expirationSecond            <b>(20)</b> The expiration time of the token, in seconds since the epoch.
         * @param memo                        <b>(21)</b> An optional description of the token with UTF-8 encoding up to
         *                                    100 bytes.
         * @param maxSupply                   <b>(22)</b> The maximum supply of this token.
         * @param paused                      <b>(23)</b> The flag indicating if this token is paused.
         * @param accountsFrozenByDefault     <b>(24)</b> The flag indicating if this token has accounts associated to
         *                                    it that are frozen by default.
         * @param accountsKycGrantedByDefault <b>(25)</b> The flag indicating if this token has accounts associated with
         *                                    it that are KYC granted by default.
         * @param customFeesSupplier          <b>(26)</b> (Optional) The custom fees of this token wrapped in a
         *                                    Supplier.
         * @param metadata                    <b>(27)</b> Metadata of the created token definition
         * @param metadataKey                 <b>(28)</b> The key which can change the metadata of a token
         *                                    (token definition and individual NFTs).
         */
        @SuppressWarnings("java:S107")
        public Builder(
                TokenID tokenId,
                String name,
                String symbol,
                int decimals,
                Supplier<Long> totalSupplySupplier,
                AccountID treasuryAccountId,
                Key adminKey,
                Key kycKey,
                Key freezeKey,
                Key wipeKey,
                Key supplyKey,
                Key feeScheduleKey,
                Key pauseKey,
                long lastUsedSerialNumber,
                boolean deleted,
                TokenType tokenType,
                TokenSupplyType supplyType,
                AccountID autoRenewAccountId,
                long autoRenewSeconds,
                long expirationSecond,
                String memo,
                long maxSupply,
                boolean paused,
                boolean accountsFrozenByDefault,
                boolean accountsKycGrantedByDefault,
                Supplier<List<CustomFee>> customFeesSupplier,
                Bytes metadata,
                Key metadataKey) {
            this.tokenId = tokenId;
            this.name = name != null ? name : "";
            this.symbol = symbol != null ? symbol : "";
            this.decimals = decimals;
            this.totalSupplySupplier = totalSupplySupplier;
            this.treasuryAccountId = treasuryAccountId;
            this.adminKey = adminKey;
            this.kycKey = kycKey;
            this.freezeKey = freezeKey;
            this.wipeKey = wipeKey;
            this.supplyKey = supplyKey;
            this.feeScheduleKey = feeScheduleKey;
            this.pauseKey = pauseKey;
            this.lastUsedSerialNumber = lastUsedSerialNumber;
            this.deleted = deleted;
            this.tokenType = tokenType;
            this.supplyType = supplyType;
            this.autoRenewAccountId = autoRenewAccountId;
            this.autoRenewSeconds = autoRenewSeconds;
            this.expirationSecond = expirationSecond;
            this.memo = memo != null ? memo : "";
            this.maxSupply = maxSupply;
            this.paused = paused;
            this.accountsFrozenByDefault = accountsFrozenByDefault;
            this.accountsKycGrantedByDefault = accountsKycGrantedByDefault;
            this.customFeesSupplier = customFeesSupplier;
            this.metadata = metadata != null ? metadata : Bytes.EMPTY;
            this.metadataKey = metadataKey;
        }

        /**
         * Build a new model record with data set on builder
         *
         * @return new model record with data set
         */
        public Token build() {
            return new Token(
                    tokenId,
                    name,
                    symbol,
                    decimals,
                    totalSupplySupplier,
                    treasuryAccountId,
                    adminKey,
                    kycKey,
                    freezeKey,
                    wipeKey,
                    supplyKey,
                    feeScheduleKey,
                    pauseKey,
                    lastUsedSerialNumber,
                    deleted,
                    tokenType,
                    supplyType,
                    autoRenewAccountId,
                    autoRenewSeconds,
                    expirationSecond,
                    memo,
                    maxSupply,
                    paused,
                    accountsFrozenByDefault,
                    accountsKycGrantedByDefault,
                    customFeesSupplier,
                    metadata,
                    metadataKey);
        }

        /**
         * <b>(1)</b> The unique entity id of this token.
         *
         * @param tokenId value to set
         * @return builder to continue building with
         */
        public Builder tokenId(@Nullable TokenID tokenId) {
            this.tokenId = tokenId;
            return this;
        }

        /**
         * <b>(1)</b> The unique entity id of this token.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder tokenId(TokenID.Builder builder) {
            this.tokenId = builder.build();
            return this;
        }

        /**
         * <b>(2)</b> The human-readable name of this token. Need not be unique. Maximum length allowed is 100 bytes.
         *
         * @param name value to set
         * @return builder to continue building with
         */
        public Builder name(@Nonnull String name) {
            this.name = name;
            return this;
        }

        /**
         * <b>(3)</b> The human-readable symbol for the token. It is not necessarily unique. Maximum length allowed is
         * 100 bytes.
         *
         * @param symbol value to set
         * @return builder to continue building with
         */
        public Builder symbol(@Nonnull String symbol) {
            this.symbol = symbol;
            return this;
        }

        /**
         * <b>(4)</b> The number of decimal places of this token. If decimals are 8 or 11, then the number of whole
         * tokens can be at most a few billions or millions, respectively. For example, it could match Bitcoin (21
         * million whole tokens with 8 decimals) or hbars (50 billion whole tokens with 8 decimals). It could even match
         * Bitcoin with milli-satoshis (21 million whole tokens with 11 decimals).
         *
         * @param decimals value to set
         * @return builder to continue building with
         */
        public Builder decimals(int decimals) {
            this.decimals = decimals;
            return this;
        }

        /**
         * <b>(5)</b> The total supply of this token.
         *
         * @param totalSupply value to set
         * @return builder to continue building with
         */
        public Builder totalSupply(long totalSupply) {
            this.totalSupplySupplier = () -> totalSupply;
            return this;
        }

        /**
         * <b>(5)</b> The total supply of this token.
         *
         * @param totalSupplySupplier value to set
         * @return builder to continue building with
         */
        public Builder totalSupply(Supplier<Long> totalSupplySupplier) {
            this.totalSupplySupplier = totalSupplySupplier;
            return this;
        }

        /**
         * <b>(6)</b> The treasury account id of this token. This account receives the initial supply of
         * tokens as well as the tokens from the Token Mint operation once executed. The balance of the treasury account
         * is decreased when the Token Burn operation is executed.
         *
         * @param treasuryAccountId value to set
         * @return builder to continue building with
         */
        public Builder treasuryAccountId(@Nullable AccountID treasuryAccountId) {
            this.treasuryAccountId = treasuryAccountId;
            return this;
        }

        /**
         * <b>(7)</b> (Optional) The admin key of this token. If this key is set, the token is mutable.
         * A mutable token can be modified. If this key is not set on token creation, it cannot be modified.
         *
         * @param adminKey value to set
         * @return builder to continue building with
         */
        public Builder adminKey(@Nullable Key adminKey) {
            this.adminKey = adminKey;
            return this;
        }

        /**
         * <b>(7)</b> (Optional) The admin key of this token. If this key is set, the token is mutable.
         * A mutable token can be modified. If this key is not set on token creation, it cannot be modified.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder adminKey(Key.Builder builder) {
            this.adminKey = builder.build();
            return this;
        }

        /**
         * <b>(8)</b> (Optional) The kyc key of this token.
         * If this key is not set on token creation, it can only be set if the token has admin key set.
         *
         * @param kycKey value to set
         * @return builder to continue building with
         */
        public Builder kycKey(@Nullable Key kycKey) {
            this.kycKey = kycKey;
            return this;
        }

        /**
         * <b>(8)</b> (Optional) The kyc key of this token.
         * If this key is not set on token creation, it can only be set if the token has admin key set.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder kycKey(Key.Builder builder) {
            this.kycKey = builder.build();
            return this;
        }

        /**
         * <b>(9)</b> (Optional) The freeze key of this token. This key is needed for freezing the token.
         * If this key is not set on token creation, it can only be set if the token has admin key set.
         *
         * @param freezeKey value to set
         * @return builder to continue building with
         */
        public Builder freezeKey(@Nullable Key freezeKey) {
            this.freezeKey = freezeKey;
            return this;
        }

        /**
         * <b>(9)</b> (Optional) The freeze key of this token. This key is needed for freezing the token.
         * If this key is not set on token creation, it can only be set if the token has admin key set.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder freezeKey(Key.Builder builder) {
            this.freezeKey = builder.build();
            return this;
        }

        /**
         * <b>(10)</b> (Optional) The wipe key of this token. This key is needed for wiping the token.
         * If this key is not set on token creation, it can only be set if the token has admin key set.
         *
         * @param wipeKey value to set
         * @return builder to continue building with
         */
        public Builder wipeKey(@Nullable Key wipeKey) {
            this.wipeKey = wipeKey;
            return this;
        }

        /**
         * <b>(10)</b> (Optional) The wipe key of this token. This key is needed for wiping the token.
         * If this key is not set on token creation, it can only be set if the token has admin key set.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder wipeKey(Key.Builder builder) {
            this.wipeKey = builder.build();
            return this;
        }

        /**
         * <b>(11)</b> (Optional) The supply key of this token. This key is needed for minting or burning token.
         * If this key is not set on token creation, it can only be set if the token has admin key set.
         *
         * @param supplyKey value to set
         * @return builder to continue building with
         */
        public Builder supplyKey(@Nullable Key supplyKey) {
            this.supplyKey = supplyKey;
            return this;
        }

        /**
         * <b>(11)</b> (Optional) The supply key of this token. This key is needed for minting or burning token.
         * If this key is not set on token creation, it can only be set if the token has admin key set.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder supplyKey(Key.Builder builder) {
            this.supplyKey = builder.build();
            return this;
        }

        /**
         * <b>(12)</b> (Optional) The fee schedule key of this token. This key should be set, in order to make any
         * changes to the custom fee schedule. If this key is not set on token creation, it can only be set if the token
         * has admin key set.
         *
         * @param feeScheduleKey value to set
         * @return builder to continue building with
         */
        public Builder feeScheduleKey(@Nullable Key feeScheduleKey) {
            this.feeScheduleKey = feeScheduleKey;
            return this;
        }

        /**
         * <b>(12)</b> (Optional) The fee schedule key of this token. This key should be set, in order to make any
         * changes to the custom fee schedule. If this key is not set on token creation, it can only be set if the token
         * has admin key set.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder feeScheduleKey(Key.Builder builder) {
            this.feeScheduleKey = builder.build();
            return this;
        }

        /**
         * <b>(13)</b> (Optional) The pause key of this token. This key is needed for pausing the token.
         * If this key is not set on token creation, it can only be set if the token has admin key set.
         *
         * @param pauseKey value to set
         * @return builder to continue building with
         */
        public Builder pauseKey(@Nullable Key pauseKey) {
            this.pauseKey = pauseKey;
            return this;
        }

        /**
         * <b>(13)</b> (Optional) The pause key of this token. This key is needed for pausing the token.
         * If this key is not set on token creation, it can only be set if the token has admin key set.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder pauseKey(Key.Builder builder) {
            this.pauseKey = builder.build();
            return this;
        }

        /**
         * <b>(14)</b> The last used serial number of this token.
         *
         * @param lastUsedSerialNumber value to set
         * @return builder to continue building with
         */
        public Builder lastUsedSerialNumber(long lastUsedSerialNumber) {
            this.lastUsedSerialNumber = lastUsedSerialNumber;
            return this;
        }

        /**
         * <b>(15)</b> The flag indicating if this token is deleted.
         *
         * @param deleted value to set
         * @return builder to continue building with
         */
        public Builder deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        /**
         * <b>(16)</b> The type of this token. A token can be either FUNGIBLE_COMMON or NON_FUNGIBLE_UNIQUE.
         * If it has been omitted during token creation, FUNGIBLE_COMMON type is used.
         *
         * @param tokenType value to set
         * @return builder to continue building with
         */
        public Builder tokenType(TokenType tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        /**
         * <b>(17)</b> The supply type of this token.A token can have either INFINITE or FINITE supply type.
         * If it has been omitted during token creation, INFINITE type is used.
         *
         * @param supplyType value to set
         * @return builder to continue building with
         */
        public Builder supplyType(TokenSupplyType supplyType) {
            this.supplyType = supplyType;
            return this;
        }

        /**
         * <b>(18)</b> The id of the account (if any) that the network will attempt to charge for the
         * token's auto-renewal upon expiration.
         *
         * @param autoRenewAccountId value to set
         * @return builder to continue building with
         */
        public Builder autoRenewAccountId(@Nullable AccountID autoRenewAccountId) {
            this.autoRenewAccountId = autoRenewAccountId;
            return this;
        }

        /**
         * <b>(19)</b> The number of seconds the network should automatically extend the token's expiration by, if the
         * token has a valid auto-renew account, and is not deleted upon expiration. If this is not provided in a
         * allowed range on token creation, the transaction will fail with INVALID_AUTO_RENEWAL_PERIOD. The default
         * values for the minimum period and maximum period are 30 days and 90 days, respectively.
         *
         * @param autoRenewSeconds value to set
         * @return builder to continue building with
         */
        public Builder autoRenewSeconds(long autoRenewSeconds) {
            this.autoRenewSeconds = autoRenewSeconds;
            return this;
        }

        /**
         * <b>(20)</b> The expiration time of the token, in seconds since the epoch.
         *
         * @param expirationSecond value to set
         * @return builder to continue building with
         */
        public Builder expirationSecond(long expirationSecond) {
            this.expirationSecond = expirationSecond;
            return this;
        }

        /**
         * <b>(21)</b> An optional description of the token with UTF-8 encoding up to 100 bytes.
         *
         * @param memo value to set
         * @return builder to continue building with
         */
        public Builder memo(@Nonnull String memo) {
            this.memo = memo;
            return this;
        }

        /**
         * <b>(22)</b> The maximum supply of this token.
         *
         * @param maxSupply value to set
         * @return builder to continue building with
         */
        public Builder maxSupply(long maxSupply) {
            this.maxSupply = maxSupply;
            return this;
        }

        /**
         * <b>(23)</b> The flag indicating if this token is paused.
         *
         * @param paused value to set
         * @return builder to continue building with
         */
        public Builder paused(boolean paused) {
            this.paused = paused;
            return this;
        }

        /**
         * <b>(24)</b> The flag indicating if this token has accounts associated to it that are frozen by default.
         *
         * @param accountsFrozenByDefault value to set
         * @return builder to continue building with
         */
        public Builder accountsFrozenByDefault(boolean accountsFrozenByDefault) {
            this.accountsFrozenByDefault = accountsFrozenByDefault;
            return this;
        }

        /**
         * <b>(25)</b> The flag indicating if this token has accounts associated with it that are KYC granted by
         * default.
         *
         * @param accountsKycGrantedByDefault value to set
         * @return builder to continue building with
         */
        public Builder accountsKycGrantedByDefault(boolean accountsKycGrantedByDefault) {
            this.accountsKycGrantedByDefault = accountsKycGrantedByDefault;
            return this;
        }

        /**
         * <b>(26)</b> (Optional) The custom fees of this token.
         *
         * @param customFees value to set
         * @return builder to continue building with
         */
        public Builder customFees(@Nonnull List<CustomFee> customFees) {
            this.customFeesSupplier = () -> customFees;
            return this;
        }

        /**
         * <b>(26)</b> (Optional) The custom fees of this token.
         *
         * @param customFeesSupplier value to set
         * @return builder to continue building with
         */
        public Builder customFees(@Nonnull Supplier<List<CustomFee>> customFeesSupplier) {
            this.customFeesSupplier = customFeesSupplier;
            return this;
        }

        /**
         * <b>(27)</b> Metadata of the created token definition
         *
         * @param metadata value to set
         * @return builder to continue building with
         */
        public Builder metadata(@Nonnull Bytes metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * <b>(28)</b> The key which can change the metadata of a token
         * (token definition and individual NFTs).
         *
         * @param metadataKey value to set
         * @return builder to continue building with
         */
        public Builder metadataKey(@Nullable Key metadataKey) {
            this.metadataKey = metadataKey;
            return this;
        }

        /**
         * <b>(28)</b> The key which can change the metadata of a token
         * (token definition and individual NFTs).
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder metadataKey(Key.Builder builder) {
            this.metadataKey = builder.build();
            return this;
        }
    }
}
