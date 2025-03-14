// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIAL_NUMBER_LIMIT_REACHED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_MAX_SUPPLY_REACHED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Copied model from hedera-services.
 * <p>
 * Encapsulates the state and operations of a Hedera token.
 *
 * <p>Operations are validated, and throw a {@link InvalidTransactionException} with response code
 * capturing the failure when one occurs.
 *
 * <p><b>NOTE:</b> Some operations only apply to specific token types. For example, a {@link
 * Token#mint(TokenRelationship, long, boolean)} call only makes sense for a token of type {@code FUNGIBLE_COMMON}; the
 * signature for a {@code NON_FUNGIBLE_UNIQUE} is
 * {@link Token#mint(TokenRelationship, List, RichInstant)}.
 * <p>
 * This model is used as a value in a special state (CachingStateFrame), used for speculative write operations. Object
 * immutability is required for this model in order to be used seamlessly in the state.
 * <p>
 * Differences from the original:
 * 1. Added factory method that returns empty instance
 * 2. Added mapToDomain method from TokenTypesManager
 * 3. Removed OwnershipTracker
 */
public class Token {
    static final long MAX_UNIQUE_NFTS_PER_TOKEN = 0xFFFFFFFFL;
    private final Long entityId;
    private final Id id;
    private final List<UniqueToken> mintedUniqueTokens;
    private final List<UniqueToken> removedUniqueTokens;
    private final Map<Long, UniqueToken> loadedUniqueTokens;
    private final boolean supplyHasChanged;
    private final TokenType type;
    private final TokenSupplyType supplyType;
    private final Supplier<Long> totalSupply;
    private final long maxSupply;
    private final JKey kycKey;
    private final JKey freezeKey;
    private final JKey supplyKey;
    private final JKey wipeKey;
    private final JKey adminKey;
    private final JKey feeScheduleKey;
    private final JKey pauseKey;
    private final boolean frozenByDefault;
    private final Supplier<Account> treasury;
    private final Supplier<Account> autoRenewAccount;
    private final boolean deleted;
    private final boolean paused;
    private final boolean autoRemoved;
    private final long expiry;
    private final boolean isNew;
    private final String memo;
    private final String name;
    private final String symbol;
    private final int decimals;
    private final long autoRenewPeriod;
    private final long createdTimestamp;
    private final long lastUsedSerialNumber;
    private final Supplier<List<CustomFee>> customFees;

    public Token(Id id) {
        this(
                0L,
                id,
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                false,
                false,
                false,
                0,
                0,
                false,
                "",
                "",
                "",
                0,
                0,
                0,
                null);
    }

    @SuppressWarnings("java:S107")
    public Token(
            Long entityId,
            Id id,
            List<UniqueToken> mintedUniqueTokens,
            List<UniqueToken> removedUniqueTokens,
            Map<Long, UniqueToken> loadedUniqueTokens,
            boolean supplyHasChanged,
            TokenType type,
            TokenSupplyType supplyType,
            Supplier<Long> totalSupply,
            long maxSupply,
            JKey kycKey,
            JKey freezeKey,
            JKey supplyKey,
            JKey wipeKey,
            JKey adminKey,
            JKey feeScheduleKey,
            JKey pauseKey,
            boolean frozenByDefault,
            Supplier<Account> treasury,
            Supplier<Account> autoRenewAccount,
            boolean deleted,
            boolean paused,
            boolean autoRemoved,
            long expiry,
            long createdTimestamp,
            boolean isNew,
            String memo,
            String name,
            String symbol,
            int decimals,
            long autoRenewPeriod,
            long lastUsedSerialNumber,
            Supplier<List<CustomFee>> customFees) {
        this.entityId = entityId;
        this.id = id;
        this.mintedUniqueTokens =
                Collections.emptyList().equals(mintedUniqueTokens) ? new ArrayList<>() : mintedUniqueTokens;
        this.removedUniqueTokens =
                Collections.emptyList().equals(removedUniqueTokens) ? new ArrayList<>() : removedUniqueTokens;
        this.loadedUniqueTokens =
                Collections.emptyMap().equals(loadedUniqueTokens) ? new HashMap<>() : loadedUniqueTokens;
        this.supplyHasChanged = supplyHasChanged;
        this.type = type;
        this.supplyType = supplyType;
        this.totalSupply = totalSupply;
        this.maxSupply = maxSupply;
        this.kycKey = kycKey;
        this.freezeKey = freezeKey;
        this.supplyKey = supplyKey;
        this.wipeKey = wipeKey;
        this.adminKey = adminKey;
        this.feeScheduleKey = feeScheduleKey;
        this.pauseKey = pauseKey;
        this.frozenByDefault = frozenByDefault;
        this.treasury = treasury;
        this.autoRenewAccount = autoRenewAccount;
        this.deleted = deleted;
        this.paused = paused;
        this.autoRemoved = autoRemoved;
        this.expiry = expiry;
        this.createdTimestamp = createdTimestamp;
        this.isNew = isNew;
        this.memo = memo;
        this.name = name;
        this.symbol = symbol;
        this.decimals = decimals;
        this.autoRenewPeriod = autoRenewPeriod;
        this.lastUsedSerialNumber = lastUsedSerialNumber;
        this.customFees = customFees;
    }

    public static Token getEmptyToken() {
        return new Token(Id.DEFAULT);
    }

    /**
     * Creates a new instance of the model token, which is later persisted in state.
     *
     * @param tokenId            the new token id
     * @param op                 the transaction body containing the necessary data for token creation
     * @param treasury           treasury of the token
     * @param autoRenewAccount   optional(nullable) account used for auto-renewal
     * @param consensusTimestamp the consensus time of the token create transaction
     * @return a new instance of the {@link Token} class
     */
    public static Token fromGrpcOpAndMeta(
            final Id tokenId,
            final TokenCreateTransactionBody op,
            final Account treasury,
            @Nullable final Account autoRenewAccount,
            final long consensusTimestamp) {
        final var tokenExpiry = op.hasAutoRenewAccount()
                ? consensusTimestamp + op.getAutoRenewPeriod().getSeconds()
                : op.getExpiry().getSeconds();

        final var freezeKey = asUsableFcKey(op.getFreezeKey());
        final var adminKey = asUsableFcKey(op.getAdminKey());
        final var kycKey = asUsableFcKey(op.getKycKey());
        final var wipeKey = asUsableFcKey(op.getWipeKey());
        final var supplyKey = asUsableFcKey(op.getSupplyKey());
        final var feeScheduleKey = asUsableFcKey(op.getFeeScheduleKey());
        final var pauseKey = asUsableFcKey(op.getPauseKey());
        return new Token(
                0L,
                tokenId,
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                mapToDomain(op.getTokenType()),
                op.getSupplyType(),
                () -> 0L,
                op.getMaxSupply(),
                kycKey.orElse(null),
                freezeKey.orElse(null),
                supplyKey.orElse(null),
                wipeKey.orElse(null),
                adminKey.orElse(null),
                feeScheduleKey.orElse(null),
                pauseKey.orElse(null),
                op.getFreezeDefault(),
                () -> treasury,
                () -> autoRenewAccount,
                false,
                false,
                false,
                tokenExpiry,
                0,
                true,
                op.getMemo(),
                op.getName(),
                op.getSymbol(),
                op.getDecimals(),
                op.getAutoRenewPeriod().getSeconds(),
                0,
                Collections::emptyList);
    }

    // copied from TokenTypesManager in services
    private static TokenType mapToDomain(final com.hederahashgraph.api.proto.java.TokenType grpcType) {
        if (grpcType == com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE) {
            return TokenType.NON_FUNGIBLE_UNIQUE;
        } else {
            return TokenType.FUNGIBLE_COMMON;
        }
    }

    public boolean isEmptyToken() {
        return this.equals(getEmptyToken());
    }

    /**
     * Creates new instance of {@link Token} with updated loadedUniqueTokens in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldToken
     * @param loadedUniqueTokens
     * @return the new instance of {@link Token} with updated {@link #loadedUniqueTokens} property
     */
    private Token createNewTokenWithLoadedUniqueTokens(Token oldToken, Map<Long, UniqueToken> loadedUniqueTokens) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated maxSupply in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldToken
     * @param maxSupply
     * @return the new instance of {@link Token} with updated {@link #maxSupply} property
     */
    private Token createNewTokenWithNewMaxSupply(Token oldToken, long maxSupply) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated totalSupply in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldToken
     * @param totalSupply
     * @return the new instance of {@link Token} with updated {@link #totalSupply} property
     */
    private Token createNewTokenWithNewTotalSupply(Token oldToken, long totalSupply) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                true,
                oldToken.type,
                oldToken.supplyType,
                () -> totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated treasury in order to keep the object's
     * immutability and avoid entry points for changing the state.
     *
     * @param oldToken
     * @param treasury
     * @return new instance of {@link Token} with updated {@link #treasury} property
     */
    private Token createNewTokenWithNewTreasury(Token oldToken, Account treasury) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                () -> treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated lastUsedSerialNumber in order to keep the object's
     * immutability and avoid entry points for changing the state.
     *
     * @param oldToken
     * @param lastUsedSerialNumber
     * @return new instance of {@link Token} with updated {@link #lastUsedSerialNumber} property
     */
    private Token createNewTokenWithNewLastUsedSerialNumber(Token oldToken, long lastUsedSerialNumber) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with deleted property in order to keep the object's
     * immutability and avoid entry points for changing the state.
     *
     * @param oldToken
     * @param isDeleted oldToken.entityId,
     * @return new instance of {@link Token} with {@link #deleted} property
     */
    private Token createNewTokenWithDeletedFlag(Token oldToken, boolean isDeleted) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                isDeleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated type in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param tokenType
     * @return new instance of {@link Token} with updated {@link #type} property
     */
    private Token createNewTokenWithTokenType(Token oldToken, TokenType tokenType) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                tokenType,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated kycKey in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param kycKey
     * @return new instance of {@link Token} with updated {@link #kycKey} property
     */
    private Token createNewTokenWithKycKey(Token oldToken, JKey kycKey) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated freezeKey in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param freezeKey
     * @return new instance of {@link Token} with updated {@link #freezeKey} property
     */
    private Token createNewTokenWithFreezeKey(Token oldToken, JKey freezeKey) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated supplyKey in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param supplyKey
     * @return new instance of {@link Token} with updated {@link #supplyKey} property
     */
    private Token createNewTokenWithSupplyKey(Token oldToken, JKey supplyKey) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated wipeKey in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param wipeKey
     * @return new instance of {@link Token} with updated {@link #wipeKey} property
     */
    private Token createNewTokenWithWipeKey(Token oldToken, JKey wipeKey) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated treasury in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param treasury
     * @return new instance of {@link Token} with updated {@link #treasury} property
     */
    private Token createNewTokenWithTreasury(Token oldToken, Account treasury) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                () -> treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated paused in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param paused
     * @return new instance of {@link Token} with updated {@link #paused} property
     */
    private Token createNewTokenWithPaused(Token oldToken, boolean paused) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated decimals in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param decimals
     * @return new instance of {@link Token} with updated {@link #decimals} property
     */
    private Token createNewTokenWithDecimals(Token oldToken, int decimals) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated removed unique tokens in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param removedUniqueTokens
     * @return new instance of {@link Token} with updated {@link #removedUniqueTokens} property
     */
    private Token createNewTokenWithRemovedUniqueTokens(Token oldToken, List<UniqueToken> removedUniqueTokens) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }
    /**
     * Creates new instance of {@link Token} with updated minted unique tokens in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param mintedUniqueTokens
     * @return new instance of {@link Token} with updated {@link #mintedUniqueTokens} property
     */
    private Token createNewTokenWithMintedUniqueTokens(Token oldToken, List<UniqueToken> mintedUniqueTokens) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated autoRenewAccount in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param autoRenewAccount
     * @return new instance of {@link Token} with updated {@link #autoRenewAccount} property
     */
    private Token createNewTokenWithAutoRenewAccount(Token oldToken, Account autoRenewAccount) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                () -> autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated autoRenewPeriod in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param autoRenewPeriod
     * @return new instance of {@link Token} with updated {@link #autoRenewPeriod} property
     */
    private Token createNewTokenWithAutoRenewPeriod(Token oldToken, long autoRenewPeriod) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated expiry in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param expiry
     * @return new instance of {@link Token} with updated {@link #expiry} property
     */
    private Token createNewTokenWithExpiry(Token oldToken, long expiry) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated adminKey in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param adminKey
     * @return new instance of {@link Token} with updated {@link #adminKey} property
     */
    private Token createNewTokenWithAdminKey(Token oldToken, JKey adminKey) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated name in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param name
     * @return new instance of {@link Token} with updated {@link #name} property
     */
    private Token createNewTokenWithName(Token oldToken, String name) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated symbol in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param symbol
     * @return new instance of {@link Token} with updated {@link #symbol} property
     */
    private Token createNewTokenWithSymbol(Token oldToken, String symbol) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated pauseKey in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param pauseKey
     * @return new instance of {@link Token} with updated {@link #pauseKey} property
     */
    private Token createNewTokenWithPauseKey(Token oldToken, JKey pauseKey) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated feeScheduleKey in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param feeScheduleKey
     * @return new instance of {@link Token} with updated {@link #feeScheduleKey} property
     */
    private Token createNewTokenWithFeeScheduleKey(Token oldToken, JKey feeScheduleKey) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated memo in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param memo
     * @return new instance of {@link Token} with updated {@link #memo} property
     */
    private Token createNewTokenWithMemo(Token oldToken, String memo) {
        return new Token(
                oldToken.entityId,
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.createdTimestamp,
                oldToken.isNew,
                memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Minting fungible tokens increases the supply and sets new balance to the treasuryRel
     *
     * @param treasuryRel
     * @param amount
     * @param ignoreSupplyKey
     * @return new instance of {@link Token} with updated fields to keep the object's immutability
     */
    public TokenModificationResult mint(
            final TokenRelationship treasuryRel, final long amount, final boolean ignoreSupplyKey) {
        validateTrue(amount >= 0, INVALID_TOKEN_MINT_AMOUNT, errorMessage("mint", amount, treasuryRel));
        validateTrue(
                type == TokenType.FUNGIBLE_COMMON,
                FAIL_INVALID,
                "Fungible mint can be invoked only on fungible token type");

        return changeSupply(treasuryRel, amount, INVALID_TOKEN_MINT_AMOUNT, ignoreSupplyKey);
    }

    /**
     * Minting unique tokens creates new instances of the given base unique token. Increments the serial number of the
     * given base unique token, and assigns each of the numbers to each new unique token instance.
     *
     * @param treasuryRel  - the relationship between the treasury account and the token
     * @param metadata     - a list of user-defined metadata, related to the nft instances.
     * @param creationTime - the consensus time of the token mint transaction
     * @return new instance of {@link Token} with updated fields to keep the object's immutability
     */
    public TokenModificationResult mint(
            final TokenRelationship treasuryRel, final List<ByteString> metadata, final RichInstant creationTime) {
        final var metadataCount = metadata.size();
        validateFalse(metadata.isEmpty(), INVALID_TOKEN_MINT_METADATA, "Cannot mint zero unique tokens");
        validateTrue(
                type == TokenType.NON_FUNGIBLE_UNIQUE,
                FAIL_INVALID,
                "Non-fungible mint can be invoked only on non-fungible token type");
        validateTrue((lastUsedSerialNumber + metadataCount) <= MAX_UNIQUE_NFTS_PER_TOKEN, SERIAL_NUMBER_LIMIT_REACHED);
        var tokenMod = changeSupply(treasuryRel, metadataCount, FAIL_INVALID, false);
        long newLastUsedSerialNumber = this.lastUsedSerialNumber;

        Token tokenWithMintedUniqueTokens = null;
        for (final ByteString m : metadata) {
            newLastUsedSerialNumber++;
            // The default sentinel account is used (0.0.0) to represent unique tokens owned by the
            // Treasury
            final var uniqueToken =
                    new UniqueToken(id, newLastUsedSerialNumber, creationTime, Id.DEFAULT, Id.DEFAULT, m.toByteArray());
            final var newMintedTokens = new ArrayList<>(mintedUniqueTokens);
            newMintedTokens.add(uniqueToken);
            tokenWithMintedUniqueTokens = tokenMod.token().setMintedUniqueTokens(newMintedTokens);
        }
        var newTreasury = getTreasury().setOwnedNfts(getTreasury().getOwnedNfts() + metadataCount);
        var newToken = createNewTokenWithNewTreasury(
                tokenWithMintedUniqueTokens != null ? tokenWithMintedUniqueTokens : tokenMod.token(), newTreasury);
        return new TokenModificationResult(
                createNewTokenWithNewLastUsedSerialNumber(newToken, newLastUsedSerialNumber),
                tokenMod.tokenRelationship());
    }

    /**
     * Burning fungible tokens reduces the supply and sets new balance to the treasuryRel
     *
     * @param treasuryRel- the relationship between the treasury account and the token
     * @param amount
     * @return new instance of {@link Token} with updated fields to keep the object's immutability
     */
    public TokenModificationResult burn(final TokenRelationship treasuryRel, final long amount) {
        validateTrue(amount >= 0, INVALID_TOKEN_BURN_AMOUNT, errorMessage("burn", amount, treasuryRel));
        return changeSupply(treasuryRel, -amount, INVALID_TOKEN_BURN_AMOUNT, false);
    }

    /**
     * Burning unique tokens effectively destroys them, as well as reduces the total supply of the token.
     *
     * @param treasuryRel-  the relationship between the treasury account and the token
     * @param serialNumbers - the serial numbers, representing the unique tokens which will be
     *                      destroyed.
     */
    public TokenModificationResult burn(final TokenRelationship treasuryRel, final List<Long> serialNumbers) {
        validateTrue(type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID);
        validateFalse(serialNumbers.isEmpty(), INVALID_TOKEN_BURN_METADATA);
        final var treasuryId = getTreasury().getId();
        final var newRemovedUniqueTokens = new ArrayList<>(removedUniqueTokens);
        for (final long serialNum : serialNumbers) {
            final var uniqueToken = loadedUniqueTokens.get(serialNum);
            validateTrue(uniqueToken != null, FAIL_INVALID);

            // Compare directly owner to treasury, oposed to hedera-services logic where owner is set to Id.DEFAULT if
            // it is treasury.
            // We have a different workflow in setting the owner value
            final var treasuryIsOwner = uniqueToken.getOwner().equals(treasuryId);
            validateTrue(treasuryIsOwner, TREASURY_MUST_OWN_BURNED_NFT);
            newRemovedUniqueTokens.add(
                    new UniqueToken(id, serialNum, RichInstant.MISSING_INSTANT, treasuryId, Id.DEFAULT, new byte[] {}));
        }
        final var numBurned = serialNumbers.size();
        var newTreasury = getTreasury().setOwnedNfts(getTreasury().getOwnedNfts() - numBurned);
        var tokenMod = changeSupply(treasuryRel, -numBurned, FAIL_INVALID, false);
        final var tokenWithRemovedUniqueTokens = tokenMod.token().setRemovedUniqueTokens(newRemovedUniqueTokens);
        var newToken = createNewTokenWithNewTreasury(tokenWithRemovedUniqueTokens, newTreasury);
        return new TokenModificationResult(newToken, tokenMod.tokenRelationship());
    }

    /**
     * Wiping fungible tokens removes the balance of the given account, as well as reduces the total supply.
     *
     * @param accountRel - the relationship between the account which owns the tokens and the token
     * @param amount     - amount to be wiped
     * @return new instance of {@link Token} with updated fields to keep the object's immutability
     */
    public TokenModificationResult wipe(final TokenRelationship accountRel, final long amount) {
        validateTrue(
                type == TokenType.FUNGIBLE_COMMON,
                FAIL_INVALID,
                "Fungible wipe can be invoked only on Fungible token type.");
        baseWipeValidations(accountRel);
        amountWipeValidations(accountRel, amount);

        final var newTotalSupply = getTotalSupply() - amount;
        final var newAccBalance = accountRel.getBalance() - amount;

        var newAccountRel = accountRel;
        if (newAccBalance == 0) {
            final var currentNumPositiveBalances = accountRel.getAccount().getNumPositiveBalances();
            var newAccount = accountRel.getAccount().setNumPositiveBalances(currentNumPositiveBalances - 1);
            newAccountRel = accountRel.setAccount(newAccount);
        }
        return new TokenModificationResult(
                createNewTokenWithNewTotalSupply(this, newTotalSupply), newAccountRel.setBalance(newAccBalance));
    }

    /**
     * Wiping unique tokens removes the unique token instances, associated to the given account, as
     * well as reduces the total supply.
     *
     * @param accountRel    - the relationship between the account, which owns the tokens, and the
     *                      token
     * @param serialNumbers - a list of serial numbers, representing the tokens to be wiped
     */
    public TokenModificationResult wipe(final TokenRelationship accountRel, final List<Long> serialNumbers) {
        validateTrue(type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID);
        validateFalse(serialNumbers.isEmpty(), INVALID_WIPING_AMOUNT);

        baseWipeValidations(accountRel);
        for (final var serialNum : serialNumbers) {
            final var uniqueToken = loadedUniqueTokens.get(serialNum);
            validateTrue(uniqueToken != null, FAIL_INVALID);
            final var wipeAccountIsOwner =
                    uniqueToken.getOwner().equals(accountRel.getAccount().getId());
            validateTrue(wipeAccountIsOwner, ACCOUNT_DOES_NOT_OWN_WIPED_NFT);
        }

        final var newTotalSupply = getTotalSupply() - serialNumbers.size();
        final var newAccountBalance = accountRel.getBalance() - serialNumbers.size();
        var account = accountRel.getAccount();
        final var newRemovedUniqueTokens = new ArrayList<>(removedUniqueTokens);
        for (final long serialNum : serialNumbers) {
            newRemovedUniqueTokens.add(new UniqueToken(
                    id, serialNum, RichInstant.MISSING_INSTANT, account.getId(), Id.DEFAULT, new byte[] {}));
        }

        if (newAccountBalance == 0) {
            final var currentNumPositiveBalances = account.getNumPositiveBalances();
            account = account.setNumPositiveBalances(currentNumPositiveBalances - 1);
        }
        final var tokenWithRemovedUniqueTokens = setRemovedUniqueTokens(newRemovedUniqueTokens);
        var newAccountRel = accountRel.setAccount(account.setOwnedNfts(account.getOwnedNfts() - serialNumbers.size()));
        newAccountRel = newAccountRel.setBalance(newAccountBalance);
        return new TokenModificationResult(
                createNewTokenWithNewTotalSupply(tokenWithRemovedUniqueTokens, newTotalSupply), newAccountRel);
    }

    public TokenRelationship newRelationshipWith(final Account account, final boolean automaticAssociation) {
        var newRel = new TokenRelationship(
                this, account, () -> 0L, false, !hasKycKey(), false, false, automaticAssociation, 0);
        if (hasFreezeKey() && frozenByDefault) {
            newRel = newRel.setFrozen(true);
        }
        return newRel;
    }

    /**
     * Creates new {@link TokenRelationship} for the specified {@link Account} IMPORTANT: The provided account is set to
     * KYC granted and unfrozen by default
     *
     * @param account the Account for which to create the relationship
     * @return newly created {@link TokenRelationship}
     */
    public TokenRelationship newEnabledRelationship(final Account account) {
        return new TokenRelationship(this, account, () -> 0L, false, true, false, false, false, 0);
    }

    private TokenModificationResult changeSupply(
            final TokenRelationship treasuryRel,
            final long amount,
            final ResponseCodeEnum negSupplyCode,
            final boolean ignoreSupplyKey) {
        validateTrue(treasuryRel != null, FAIL_INVALID, "Cannot mint with a null treasuryRel");
        validateTrue(
                treasuryRel.hasInvolvedIds(id, getTreasury().getId()),
                FAIL_INVALID,
                "Cannot change " + this + " supply (" + amount + ") with non-treasury rel " + treasuryRel);
        if (!ignoreSupplyKey) {
            validateTrue(supplyKey != null, TOKEN_HAS_NO_SUPPLY_KEY);
        }
        final long newTotalSupply = getTotalSupply() + amount;
        validateTrue(newTotalSupply >= 0, negSupplyCode);
        if (supplyType == TokenSupplyType.FINITE) {
            validateTrue(
                    maxSupply >= newTotalSupply,
                    TOKEN_MAX_SUPPLY_REACHED,
                    "Cannot mint new supply (" + amount + "). Max supply (" + maxSupply + ") reached");
        }
        var treasuryAccount = treasuryRel.getAccount();
        final long newTreasuryBalance = treasuryRel.getBalance() + amount;
        validateTrue(newTreasuryBalance >= 0, INSUFFICIENT_TOKEN_BALANCE);
        if (treasuryRel.getBalance() == 0 && amount > 0) {
            // for mint op
            treasuryAccount = treasuryAccount.setNumPositiveBalances(treasuryAccount.getNumPositiveBalances() + 1);
        } else if (newTreasuryBalance == 0 && amount < 0) {
            // for burn op
            treasuryAccount = treasuryAccount.setNumPositiveBalances(treasuryAccount.getNumPositiveBalances() - 1);
        }
        var newTreasuryRel = treasuryRel.setAccount(treasuryAccount);
        newTreasuryRel = newTreasuryRel.setBalance(newTreasuryBalance);
        return new TokenModificationResult(createNewTokenWithNewTotalSupply(this, newTotalSupply), newTreasuryRel);
    }

    private void baseWipeValidations(final TokenRelationship accountRel) {
        validateTrue(hasWipeKey(), TOKEN_HAS_NO_WIPE_KEY, "Cannot wipe Tokens without wipe key.");

        validateFalse(
                getTreasury().getId().equals(accountRel.getAccount().getId()),
                CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT,
                "Cannot wipe treasury account of token.");
    }

    private void amountWipeValidations(final TokenRelationship accountRel, final long amount) {
        validateTrue(amount >= 0, INVALID_WIPING_AMOUNT, errorMessage("wipe", amount, accountRel));

        final var newTotalSupply = getTotalSupply() - amount;
        validateTrue(
                newTotalSupply >= 0, INVALID_WIPING_AMOUNT, "Wiping would negate the total supply of the given token.");

        final var newAccountBalance = accountRel.getBalance() - amount;
        validateTrue(newAccountBalance >= 0, INVALID_WIPING_AMOUNT, "Wiping would negate account balance");
    }

    private String errorMessage(final String op, final long amount, final TokenRelationship rel) {
        return "Cannot " + op + " " + amount + " units of " + this + " from " + rel;
    }

    public Token delete() {
        validateTrue(hasAdminKey(), TOKEN_IS_IMMUTABLE);
        return createNewTokenWithDeletedFlag(this, true);
    }

    public Token setIsDeleted(boolean isDeleted) {
        return createNewTokenWithDeletedFlag(this, isDeleted);
    }

    public boolean hasAdminKey() {
        return adminKey != null;
    }

    public Account getTreasury() {
        return treasury != null && treasury.get() != null ? treasury.get() : Account.getEmptyAccount();
    }

    public Token setTreasury(Account treasury) {
        return createNewTokenWithTreasury(this, treasury);
    }

    public Account getAutoRenewAccount() {
        return autoRenewAccount != null && autoRenewAccount.get() != null
                ? autoRenewAccount.get()
                : Account.getEmptyAccount();
    }

    public Token setAutoRenewAccount(Account autoRenewAccount) {
        return createNewTokenWithAutoRenewAccount(this, autoRenewAccount);
    }

    public boolean hasAutoRenewAccount() {
        return autoRenewAccount != null;
    }

    public long getTotalSupply() {
        return totalSupply != null && totalSupply.get() != null ? totalSupply.get() : 0L;
    }

    public long getMaxSupply() {
        return maxSupply;
    }

    public Token setMaxSupply(long maxSupply) {
        return createNewTokenWithNewMaxSupply(this, maxSupply);
    }

    public JKey getSupplyKey() {
        return supplyKey;
    }

    public Token setSupplyKey(JKey supplyKey) {
        return createNewTokenWithSupplyKey(this, supplyKey);
    }

    public boolean hasSupplyKey() {
        return supplyKey != null;
    }

    public Token setMintedUniqueTokens(final List<UniqueToken> mintedUniqueTokens) {
        return createNewTokenWithMintedUniqueTokens(this, mintedUniqueTokens);
    }

    public Token setRemovedUniqueTokens(final List<UniqueToken> removedUniqueTokens) {
        return createNewTokenWithRemovedUniqueTokens(this, removedUniqueTokens);
    }

    public boolean hasFreezeKey() {
        return freezeKey != null;
    }

    public boolean hasKycKey() {
        return kycKey != null;
    }

    public boolean hasWipeKey() {
        return wipeKey != null;
    }

    public JKey getWipeKey() {
        return wipeKey;
    }

    public Token setWipeKey(JKey wipeKey) {
        return createNewTokenWithWipeKey(this, wipeKey);
    }

    public JKey getKycKey() {
        return kycKey;
    }

    public Token setKycKey(JKey kycKey) {
        return createNewTokenWithKycKey(this, kycKey);
    }

    public JKey getFreezeKey() {
        return freezeKey;
    }

    public Token setFreezeKey(JKey freezeKey) {
        return createNewTokenWithFreezeKey(this, freezeKey);
    }

    public JKey getPauseKey() {
        return pauseKey;
    }

    public Token setPauseKey(JKey pauseKey) {
        return createNewTokenWithPauseKey(this, pauseKey);
    }

    public boolean hasPauseKey() {
        return pauseKey != null;
    }

    /* supply is changed only after the token is created */
    public boolean hasChangedSupply() {
        return supplyHasChanged && !isNew;
    }

    public boolean isFrozenByDefault() {
        return frozenByDefault;
    }

    public boolean isPaused() {
        return paused;
    }

    public Token setPaused(boolean paused) {
        return createNewTokenWithPaused(this, paused);
    }

    public Token changePauseStatus(final boolean paused) {
        validateTrue(hasPauseKey(), TOKEN_HAS_NO_PAUSE_KEY);
        return createNewTokenWithPaused(this, paused);
    }

    public Long getEntityId() {
        return entityId;
    }

    public Id getId() {
        return id;
    }

    public TokenType getType() {
        return type;
    }

    public Token setType(TokenType tokenType) {
        return createNewTokenWithTokenType(this, tokenType);
    }

    public boolean isFungibleCommon() {
        return type == TokenType.FUNGIBLE_COMMON;
    }

    public boolean isNonFungibleUnique() {
        return type == TokenType.NON_FUNGIBLE_UNIQUE;
    }

    public long getLastUsedSerialNumber() {
        return lastUsedSerialNumber;
    }

    public Token setLastUsedSerialNumber(final long lastUsedSerialNumber) {
        return createNewTokenWithNewLastUsedSerialNumber(this, lastUsedSerialNumber);
    }

    public List<CustomFee> getCustomFees() {
        return customFees != null && customFees.get() != null ? customFees.get() : Collections.emptyList();
    }

    public boolean hasMintedUniqueTokens() {
        return !mintedUniqueTokens.isEmpty();
    }

    public List<UniqueToken> mintedUniqueTokens() {
        return mintedUniqueTokens;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public long getExpiry() {
        return expiry;
    }

    public Token setExpiry(long expiry) {
        return createNewTokenWithExpiry(this, expiry);
    }

    public boolean hasRemovedUniqueTokens() {
        return !removedUniqueTokens.isEmpty();
    }

    public List<UniqueToken> removedUniqueTokens() {
        return removedUniqueTokens;
    }

    public Map<Long, UniqueToken> getLoadedUniqueTokens() {
        return loadedUniqueTokens;
    }

    public Token setLoadedUniqueTokens(final Map<Long, UniqueToken> loadedUniqueTokens) {
        return createNewTokenWithLoadedUniqueTokens(this, loadedUniqueTokens);
    }

    public boolean isBelievedToHaveBeenAutoRemoved() {
        return autoRemoved;
    }

    public JKey getAdminKey() {
        return adminKey;
    }

    public Token setAdminKey(JKey adminKey) {
        return createNewTokenWithAdminKey(this, adminKey);
    }

    public String getMemo() {
        return memo;
    }

    public Token setMemo(String memo) {
        return createNewTokenWithMemo(this, memo);
    }

    public boolean isNew() {
        return isNew;
    }

    public TokenSupplyType getSupplyType() {
        return supplyType;
    }

    public String getName() {
        return name;
    }

    public Token setName(String name) {
        return createNewTokenWithName(this, name);
    }

    public String getSymbol() {
        return symbol;
    }

    public Token setSymbol(String symbol) {
        return createNewTokenWithSymbol(this, symbol);
    }

    public int getDecimals() {
        return decimals;
    }

    public Token setDecimals(int decimals) {
        return createNewTokenWithDecimals(this, decimals);
    }

    public long getAutoRenewPeriod() {
        return autoRenewPeriod;
    }

    public Token setAutoRenewPeriod(long autoRenewPeriod) {
        return createNewTokenWithAutoRenewPeriod(this, autoRenewPeriod);
    }

    public long getCreatedTimestamp() {
        return this.createdTimestamp;
    }

    /* NOTE: The object methods below are only overridden to improve
    readability of unit tests; this model object is not used in hash-based
    collections, so the performance of these methods doesn't matter. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return supplyHasChanged == token.supplyHasChanged
                && getMaxSupply() == token.getMaxSupply()
                && isFrozenByDefault() == token.isFrozenByDefault()
                && isDeleted() == token.isDeleted()
                && isPaused() == token.isPaused()
                && autoRemoved == token.autoRemoved
                && getExpiry() == token.getExpiry()
                && isNew() == token.isNew()
                && getDecimals() == token.getDecimals()
                && getAutoRenewPeriod() == token.getAutoRenewPeriod()
                && getCreatedTimestamp() == token.getCreatedTimestamp()
                && getLastUsedSerialNumber() == token.getLastUsedSerialNumber()
                && Objects.equals(getEntityId(), token.getEntityId())
                && Objects.equals(getId(), token.getId())
                && Objects.equals(mintedUniqueTokens, token.mintedUniqueTokens)
                && Objects.equals(removedUniqueTokens, token.removedUniqueTokens)
                && Objects.equals(getLoadedUniqueTokens(), token.getLoadedUniqueTokens())
                && getType() == token.getType()
                && getSupplyType() == token.getSupplyType()
                && Objects.equals(getTotalSupply(), token.getTotalSupply())
                && Objects.equals(getKycKey(), token.getKycKey())
                && Objects.equals(getFreezeKey(), token.getFreezeKey())
                && Objects.equals(getSupplyKey(), token.getSupplyKey())
                && Objects.equals(getWipeKey(), token.getWipeKey())
                && Objects.equals(getAdminKey(), token.getAdminKey())
                && Objects.equals(getFeeScheduleKey(), token.getFeeScheduleKey())
                && Objects.equals(getPauseKey(), token.getPauseKey())
                && Objects.equals(getTreasury(), token.getTreasury())
                && Objects.equals(getAutoRenewAccount(), token.getAutoRenewAccount())
                && Objects.equals(getMemo(), token.getMemo())
                && Objects.equals(getName(), token.getName())
                && Objects.equals(getSymbol(), token.getSymbol())
                && Objects.equals(getCustomFees(), token.getCustomFees());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getEntityId(),
                getId(),
                mintedUniqueTokens,
                removedUniqueTokens,
                getLoadedUniqueTokens(),
                supplyHasChanged,
                getType(),
                getSupplyType(),
                getTotalSupply(),
                getMaxSupply(),
                getKycKey(),
                getFreezeKey(),
                getSupplyKey(),
                getWipeKey(),
                getAdminKey(),
                getFeeScheduleKey(),
                getPauseKey(),
                isFrozenByDefault(),
                getTreasury(),
                getAutoRenewAccount(),
                isDeleted(),
                isPaused(),
                autoRemoved,
                getExpiry(),
                isNew(),
                getMemo(),
                getName(),
                getSymbol(),
                getDecimals(),
                getAutoRenewPeriod(),
                getCreatedTimestamp(),
                getLastUsedSerialNumber(),
                getCustomFees());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(Token.class)
                .add("id", id)
                .add("type", type)
                .add("deleted", deleted)
                .add("autoRemoved", autoRemoved)
                .add("treasury", getTreasury())
                .add("autoRenewAccount", getAutoRenewAccount())
                .add("kycKey", kycKey)
                .add("freezeKey", freezeKey)
                .add("frozenByDefault", frozenByDefault)
                .add("supplyKey", supplyKey)
                .add("currentSerialNumber", lastUsedSerialNumber)
                .add("pauseKey", pauseKey)
                .add("paused", paused)
                .toString();
    }

    public boolean hasFeeScheduleKey() {
        return feeScheduleKey != null;
    }

    public JKey getFeeScheduleKey() {
        return feeScheduleKey;
    }

    public Token setFeeScheduleKey(JKey feeScheduleKey) {
        return createNewTokenWithFeeScheduleKey(this, feeScheduleKey);
    }
}
