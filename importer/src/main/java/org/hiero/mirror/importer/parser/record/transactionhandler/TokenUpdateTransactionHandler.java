// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.common.domain.transaction.RecordFile.HAPI_VERSION_0_49_0;

import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import jakarta.inject.Named;
import java.util.Arrays;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.repository.TokenRepository;
import org.hiero.mirror.importer.repository.TokenRepository.Treasury;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.Nullable;

@CustomLog
@Named
final class TokenUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private static final byte[] EMPTY_KEY_LIST =
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build().toByteArray();

    private final EntityProperties entityProperties;
    private final ParserContext parserContext;
    private final TokenRepository tokenRepository;

    TokenUpdateTransactionHandler(
            EntityIdService entityIdService,
            EntityListener entityListener,
            EntityProperties entityProperties,
            ParserContext parserContext,
            TokenRepository tokenRepository) {
        super(entityIdService, entityListener, TransactionType.TOKENUPDATE);
        this.entityProperties = entityProperties;
        this.parserContext = parserContext;
        this.tokenRepository = tokenRepository;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        final var transactionBody = recordItem.getTransactionBody().getTokenUpdate();

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasAutoRenewAccount()) {
            // Allow clearing of the autoRenewAccount by allowing it to be set to 0
            entityIdService
                    .lookup(transactionBody.getAutoRenewAccount())
                    .ifPresentOrElse(
                            accountId -> {
                                entity.setAutoRenewAccountId(accountId.getId());
                                recordItem.addEntityId(accountId);
                            },
                            () -> Utility.handleRecoverableError(
                                    "Invalid autoRenewAccountId at {}", recordItem.getConsensusTimestamp()));
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasExpiry()) {
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(transactionBody.getExpiry()));
        }

        if (transactionBody.hasMemo()) {
            entity.setMemo(transactionBody.getMemo().getValue());
        }

        entity.setType(EntityType.TOKEN);
        entityListener.onEntity(entity);
        updateToken(entity, recordItem);
    }

    /**
     * Owns every automatic association of the token update, including when the CRUD guard skips the token update.
     * FAIL_INVALID errata can contain committed associations, but their bodies must not be used to infer treasury
     * changes. AbstractTransactionHandler calls this before updateEntity, so the parser context still holds the token
     * prior to this update when the treasury relationship is created.
     */
    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens()
                || (!recordItem.isSuccessful()
                        && recordItem.getTransactionStatus() != ResponseCodeEnum.FAIL_INVALID_VALUE)) {
            return;
        }

        var treasuryRelationship = createTreasuryRelationship(transaction, recordItem);
        final long timestamp = recordItem.getConsensusTimestamp();
        for (final var association : recordItem.getTransactionRecord().getAutomaticTokenAssociationsList()) {
            // Consensus nodes always populate automatic associations with numeric account ids
            final var accountId = EntityId.of(association.getAccountId());
            final var tokenId = EntityId.of(association.getTokenId());
            final TokenAccount relationship;
            if (treasuryRelationship != null
                    && treasuryRelationship.getAccountId() == accountId.getId()
                    && treasuryRelationship.getTokenId() == tokenId.getId()) {
                relationship = treasuryRelationship;
                treasuryRelationship = null;
            } else {
                relationship = new TokenAccount();
                relationship.setAccountId(accountId.getId());
                relationship.setTokenId(tokenId.getId());
                relationship.setAssociated(true);
                relationship.setTimestampLower(timestamp);
            }
            relationship.setAutomaticAssociation(true);
            relationship.setCreatedTimestamp(timestamp);
            relationship.setBalanceTimestamp(timestamp);
            entityListener.onTokenAccount(relationship);
            recordItem.addEntityId(accountId);
            recordItem.addEntityId(tokenId);
        }

        if (treasuryRelationship != null) {
            entityListener.onTokenAccount(treasuryRelationship);
        }
    }

    private @Nullable TokenAccount createTreasuryRelationship(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful() || EntityId.isEmpty(transaction.getEntityId())) {
            return null;
        }

        final var treasury = resolveTreasury(recordItem);
        if (EntityId.isEmpty(treasury)) {
            return null;
        }

        final long tokenId = transaction.getEntityId().getId();
        final var original = getOriginalToken(tokenId);
        final boolean hasFreezeKey = original != null && isKeyPresent(original.freezeKey());
        final boolean hasKycKey = original != null && isKeyPresent(original.kycKey());
        if (original == null || treasury.equals(original.treasuryAccountId()) || (!hasFreezeKey && !hasKycKey)) {
            return null;
        }

        final var relationship = new TokenAccount();
        relationship.setAccountId(treasury.getId());
        relationship.setTokenId(tokenId);
        relationship.setAssociated(true);
        relationship.setTimestampLower(recordItem.getConsensusTimestamp());
        if (hasFreezeKey) {
            relationship.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
        }
        if (hasKycKey) {
            relationship.setKycStatus(TokenKycStatusEnum.GRANTED);
        }
        return relationship;
    }

    private @Nullable Treasury getOriginalToken(long tokenId) {
        final var pending = parserContext.get(Token.class, tokenId);
        if (pending != null && pending.getCreatedTimestamp() != null) {
            // A token created in this batch already has a complete snapshot, including intentionally absent keys.
            return new Treasury(pending.getTreasuryAccountId(), pending.getFreezeKey(), pending.getKycKey());
        }

        final var persisted = tokenRepository.findTreasuryByTokenId(tokenId);
        if (pending == null) {
            return persisted;
        }
        if (persisted == null) {
            return new Treasury(pending.getTreasuryAccountId(), pending.getFreezeKey(), pending.getKycKey());
        }

        // Pending token objects can contain only a supply change or partial metadata update. Overlay the fields
        // needed here without modifying either the database entity or the snapshots queued for persistence.
        return new Treasury(
                !EntityId.isEmpty(pending.getTreasuryAccountId())
                        ? pending.getTreasuryAccountId()
                        : persisted.treasuryAccountId(),
                pending.getFreezeKey() != null ? pending.getFreezeKey() : persisted.freezeKey(),
                pending.getKycKey() != null ? pending.getKycKey() : persisted.kycKey());
    }

    private static boolean isKeyPresent(byte @Nullable [] key) {
        // HIP-540 removes a key by setting it to an empty key list. Consensus clears the key from state, but mirror
        // stores the empty key list bytes as is, so it's treated as an absent key.
        return key != null && key.length > 0 && !Arrays.equals(key, EMPTY_KEY_LIST);
    }

    /**
     * Resolves the treasury in the transaction body, which may be an alias. Returns EntityId.EMPTY if the treasury is
     * not set, is the 0.0.0 account a synthetic token update can have, or is an alias that can't be resolved. Alias
     * lookups are cached by EntityIdService, so resolving it for both the treasury relationship and the token is cheap.
     */
    private EntityId resolveTreasury(RecordItem recordItem) {
        final var treasury = recordItem.getTransactionBody().getTokenUpdate().getTreasury();
        return treasury.hasAlias() ? entityIdService.lookup(treasury).orElse(EntityId.EMPTY) : EntityId.of(treasury);
    }

    private void updateToken(Entity entity, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens()) {
            return;
        }

        final var transactionBody = recordItem.getTransactionBody().getTokenUpdate();
        final var token = new Token();
        token.setTimestampLower(recordItem.getConsensusTimestamp());
        token.setTokenId(entity.getId());

        if (transactionBody.hasFeeScheduleKey()) {
            token.setFeeScheduleKey(transactionBody.getFeeScheduleKey().toByteArray());
        }

        if (transactionBody.hasFreezeKey()) {
            token.setFreezeKey(transactionBody.getFreezeKey().toByteArray());
        }

        if (transactionBody.hasKycKey()) {
            token.setKycKey(transactionBody.getKycKey().toByteArray());
        }

        // metadata and metadata key fields are supported from services 0.49.0. This is a workaround of the issue that
        // services 0.48.x processes such transactions as if the fields are not present.
        if (recordItem.getHapiVersion().isGreaterThanOrEqualTo(HAPI_VERSION_0_49_0)) {
            if (transactionBody.hasMetadata()) {
                token.setMetadata(
                        DomainUtils.toBytes(transactionBody.getMetadata().getValue()));
            }

            if (transactionBody.hasMetadataKey()) {
                token.setMetadataKey(transactionBody.getMetadataKey().toByteArray());
            }
        }

        if (!transactionBody.getName().isEmpty()) {
            token.setName(transactionBody.getName());
        }

        if (transactionBody.hasPauseKey()) {
            token.setPauseKey(transactionBody.getPauseKey().toByteArray());
        }

        if (transactionBody.hasSupplyKey()) {
            token.setSupplyKey(transactionBody.getSupplyKey().toByteArray());
        }

        if (!transactionBody.getSymbol().isEmpty()) {
            token.setSymbol(transactionBody.getSymbol());
        }

        // A synthetic token update can have a 0.0.0 treasury account populated by consensus nodes
        final var treasury = resolveTreasury(recordItem);
        if (!EntityId.isEmpty(treasury)) {
            token.setTreasuryAccountId(treasury);
            recordItem.addEntityId(treasury);
        }

        if (transactionBody.hasWipeKey()) {
            token.setWipeKey(transactionBody.getWipeKey().toByteArray());
        }

        entityListener.onToken(token);
    }
}
