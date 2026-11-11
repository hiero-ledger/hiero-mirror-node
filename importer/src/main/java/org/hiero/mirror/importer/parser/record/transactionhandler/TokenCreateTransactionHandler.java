// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum.FROZEN;
import static org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum.NOT_APPLICABLE;
import static org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum.UNFROZEN;
import static org.hiero.mirror.common.domain.transaction.RecordFile.HAPI_VERSION_0_49_0;
import static org.hiero.mirror.common.domain.transaction.RecordFile.HAPI_VERSION_0_77_0;

import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import jakarta.inject.Named;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.util.Utility;

@CustomLog
@Named
final class TokenCreateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private final EntityProperties entityProperties;
    private final TokenFeeScheduleUpdateTransactionHandler tokenFeeScheduleUpdateTransactionHandler;

    TokenCreateTransactionHandler(
            EntityIdService entityIdService,
            EntityListener entityListener,
            EntityProperties entityProperties,
            TokenFeeScheduleUpdateTransactionHandler tokenFeeScheduleUpdateTransactionHandler) {
        super(entityIdService, entityListener, TransactionType.TOKENCREATION);
        this.entityProperties = entityProperties;
        this.tokenFeeScheduleUpdateTransactionHandler = tokenFeeScheduleUpdateTransactionHandler;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionRecord().getReceipt().getTokenID());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getTokenCreation();

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasAutoRenewAccount()) {
            var autoRenewAccountId = entityIdService
                    .lookup(transactionBody.getAutoRenewAccount())
                    .orElse(EntityId.EMPTY);
            if (EntityId.isEmpty(autoRenewAccountId)) {
                Utility.handleRecoverableError("Invalid autoRenewAccountId at {}", recordItem.getConsensusTimestamp());
            } else {
                entity.setAutoRenewAccountId(autoRenewAccountId.getId());
                recordItem.addEntityId(autoRenewAccountId);
            }
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasExpiry()) {
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(transactionBody.getExpiry()));
        }

        entity.setMemo(transactionBody.getMemo());
        entity.setType(EntityType.TOKEN);
        entityListener.onEntity(entity);
    }

    @Override
    @SuppressWarnings("java:S3776")
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        final var transactionBody = recordItem.getTransactionBody().getTokenCreation();
        final long consensusTimestamp = transaction.getConsensusTimestamp();
        final boolean freezeDefault = transactionBody.getFreezeDefault();
        final var tokenId = transaction.getEntityId();
        var treasury = EntityId.tryOf(transactionBody.getTreasury());
        treasury = EntityId.isEmpty(treasury) ? EntityId.ZERO : treasury;

        final var token = new Token();
        token.setCreatedTimestamp(consensusTimestamp);
        token.setDecimals(transactionBody.getDecimals());
        token.setFreezeDefault(freezeDefault);
        token.setInitialSupply(transactionBody.getInitialSupply());
        token.setMaxSupply(transactionBody.getMaxSupply());
        token.setName(transactionBody.getName());
        token.setSupplyType(TokenSupplyTypeEnum.fromId(transactionBody.getSupplyTypeValue()));
        token.setSymbol(transactionBody.getSymbol());
        token.setTimestampLower(consensusTimestamp);
        token.setTokenId(tokenId.getId());
        token.setTotalSupply(transactionBody.getInitialSupply());
        token.setTreasuryAccountId(treasury);
        token.setType(TokenTypeEnum.fromId(transactionBody.getTokenTypeValue()));

        if (transactionBody.hasFeeScheduleKey()) {
            token.setFeeScheduleKey(transactionBody.getFeeScheduleKey().toByteArray());
        }

        if (transactionBody.hasFreezeKey()) {
            token.setFreezeKey(transactionBody.getFreezeKey().toByteArray());
        }

        if (isKeyEnabled(transactionBody.hasFreezeKey(), transactionBody.getFreezeKey(), recordItem)) {
            token.setFreezeStatus(freezeDefault ? FROZEN : UNFROZEN);
        } else {
            token.setFreezeStatus(NOT_APPLICABLE);
        }

        if (transactionBody.hasKycKey()) {
            token.setKycKey(transactionBody.getKycKey().toByteArray());
        }

        if (isKeyEnabled(transactionBody.hasKycKey(), transactionBody.getKycKey(), recordItem)) {
            token.setKycStatus(TokenKycStatusEnum.REVOKED);
        } else {
            token.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        }

        // metadata and metadata key fields are supported from services 0.49.0. This is a workaround of the issue that
        // services 0.48.x processes such transactions as if the fields are not present.
        if (recordItem.getHapiVersion().isGreaterThanOrEqualTo(HAPI_VERSION_0_49_0)) {
            token.setMetadata(DomainUtils.toBytes(transactionBody.getMetadata()));

            if (transactionBody.hasMetadataKey()) {
                token.setMetadataKey(transactionBody.getMetadataKey().toByteArray());
            }
        }

        if (transactionBody.hasPauseKey()) {
            token.setPauseKey(transactionBody.getPauseKey().toByteArray());
            token.setPauseStatus(TokenPauseStatusEnum.UNPAUSED);
        } else {
            token.setPauseStatus(TokenPauseStatusEnum.NOT_APPLICABLE);
        }

        if (transactionBody.hasSupplyKey()) {
            token.setSupplyKey(transactionBody.getSupplyKey().toByteArray());
        }

        if (transactionBody.hasWipeKey()) {
            token.setWipeKey(transactionBody.getWipeKey().toByteArray());
        }

        var customFees = transactionBody.getCustomFeesList();
        var autoAssociatedAccounts =
                tokenFeeScheduleUpdateTransactionHandler.updateCustomFees(customFees, recordItem, transaction);
        autoAssociatedAccounts.add(treasury);

        // automatic_token_associations does not exist prior to services 0.18.0
        if (recordItem.getTransactionRecord().getAutomaticTokenAssociationsCount() > 0) {
            autoAssociatedAccounts.clear();
            recordItem.getTransactionRecord().getAutomaticTokenAssociationsList().stream()
                    .map(TokenAssociation::getAccountId)
                    .map(EntityId::of)
                    .forEach(autoAssociatedAccounts::add);
        }

        final var freezeStatus = token.getFreezeStatus() != NOT_APPLICABLE ? UNFROZEN : NOT_APPLICABLE;
        final var kycStatus = token.getKycStatus() != TokenKycStatusEnum.NOT_APPLICABLE
                ? TokenKycStatusEnum.GRANTED
                : TokenKycStatusEnum.NOT_APPLICABLE;
        // Prior to services 0.49.0 (mono-service), every automatically associated account got an enabled relationship.
        // Since then, only the treasury does, and other accounts such as fee collectors get the token defaults.
        final boolean treasuryOnly = recordItem.getHapiVersion().isGreaterThanOrEqualTo(HAPI_VERSION_0_49_0);

        autoAssociatedAccounts.forEach(account -> {
            final boolean enabled = !treasuryOnly || account.equals(token.getTreasuryAccountId());
            var tokenAccount = new TokenAccount();
            tokenAccount.setAccountId(account.getId());
            tokenAccount.setAssociated(true);
            tokenAccount.setAutomaticAssociation(false);
            tokenAccount.setBalance(0L);
            tokenAccount.setBalanceTimestamp(consensusTimestamp);
            tokenAccount.setCreatedTimestamp(consensusTimestamp);
            tokenAccount.setFreezeStatus(enabled ? freezeStatus : token.getFreezeStatus());
            tokenAccount.setKycStatus(enabled ? kycStatus : token.getKycStatus());
            tokenAccount.setTimestampLower(consensusTimestamp);
            tokenAccount.setTokenId(tokenId.getId());
            entityListener.onTokenAccount(tokenAccount);

            recordItem.addEntityId(account);
        });

        entityListener.onToken(token);
    }

    /**
     * Whether the token create sets the key. Since services 0.77.0, an empty key list (the HIP-540 removal sentinel)
     * disables the key, so the token behaves as if the key isn't set and its status is NOT_APPLICABLE.
     */
    private static boolean isKeyEnabled(final boolean hasKey, final Key key, final RecordItem recordItem) {
        if (!hasKey) {
            return false;
        }

        return !key.hasKeyList()
                || key.getKeyList().getKeysCount() != 0
                || recordItem.getHapiVersion().isLessThan(HAPI_VERSION_0_77_0);
    }
}
