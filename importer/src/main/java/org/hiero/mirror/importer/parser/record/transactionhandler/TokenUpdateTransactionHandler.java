// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.common.domain.transaction.RecordFile.HAPI_VERSION_0_49_0;

import jakarta.inject.Named;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.util.Utility;

@CustomLog
@Named
class TokenUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler {

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
        var transactionBody = recordItem.getTransactionBody().getTokenUpdate();

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

    private void updateToken(Entity entity, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getTokenUpdate();
        var token = new Token();
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

        if (transactionBody.hasTreasury()) {
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
