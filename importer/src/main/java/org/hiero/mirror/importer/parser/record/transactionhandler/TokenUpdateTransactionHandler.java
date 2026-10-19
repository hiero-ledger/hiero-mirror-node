// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.common.domain.transaction.RecordFile.HAPI_VERSION_0_49_0;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import jakarta.inject.Named;
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
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.Nullable;

@CustomLog
@Named
final class TokenUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private final EntityProperties entityProperties;

    TokenUpdateTransactionHandler(
            EntityIdService entityIdService, EntityListener entityListener, EntityProperties entityProperties) {
        super(entityIdService, entityListener, TransactionType.TOKENUPDATE);
        this.entityProperties = entityProperties;
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
            final var memo = transactionBody.getMemo().getValue();
            if (!recordItem.hasContractResult() || !memo.isBlank()) {
                // A blank (empty or only spaces) memo update from precompile should not change the token's memo
                entity.setMemo(memo);
            }
        }

        entity.setType(EntityType.TOKEN);
        entityListener.onEntity(entity);
        updateToken(entity, recordItem);
    }

    /**
     * Owns every automatic association of the token update, including when the CRUD guard skips the token update.
     * FAIL_INVALID errata can contain committed associations, but their bodies must not be used to infer treasury
     * changes.
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

        final var treasury =
                EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getTreasury());
        if (EntityId.isEmpty(treasury)) {
            return null;
        }

        // Consensus nodes unfreeze the new treasury if the token has a freeze key, and grant it KYC if the token has a
        // KYC key. Both statuses are set regardless of the keys, since readers show NOT_APPLICABLE for a token without
        // the key, and a token can't get a freeze key or a KYC key after its creation.
        final var relationship = new TokenAccount();
        relationship.setAccountId(treasury.getId());
        relationship.setAssociated(true);
        relationship.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
        relationship.setKycStatus(TokenKycStatusEnum.GRANTED);
        relationship.setTimestampLower(recordItem.getConsensusTimestamp());
        relationship.setTokenId(transaction.getEntityId().getId());
        return relationship;
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
        if (transactionBody.hasTreasury() && !AccountID.getDefaultInstance().equals(transactionBody.getTreasury())) {
            var treasury = EntityId.of(transactionBody.getTreasury());
            token.setTreasuryAccountId(treasury);
            recordItem.addEntityId(treasury);
        }

        if (transactionBody.hasWipeKey()) {
            token.setWipeKey(transactionBody.getWipeKey().toByteArray());
        }

        entityListener.onToken(token);
    }
}
