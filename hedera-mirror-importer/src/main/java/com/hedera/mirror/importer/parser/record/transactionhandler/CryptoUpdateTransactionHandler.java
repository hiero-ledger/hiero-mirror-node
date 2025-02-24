// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.common.domain.transaction.RecordFile.HAPI_VERSION_0_27_0;
import static com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody.StakedIdCase.STAKEDID_NOT_SET;

import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.util.Utility;
import jakarta.inject.Named;

@Named
class CryptoUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    CryptoUpdateTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityIdService, entityListener, TransactionType.CRYPTOUPDATEACCOUNT);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(
                recordItem.getTransactionBody().getCryptoUpdateAccount().getAccountIDToUpdate());
    }

    @Override
    @SuppressWarnings({"deprecation", "java:S1874"})
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getCryptoUpdateAccount();

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasExpirationTime()) {
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(transactionBody.getExpirationTime()));
        }

        if (transactionBody.hasKey()) {
            entity.setKey(transactionBody.getKey().toByteArray());
        }

        if (transactionBody.hasMaxAutomaticTokenAssociations()) {
            entity.setMaxAutomaticTokenAssociations(
                    transactionBody.getMaxAutomaticTokenAssociations().getValue());
        }

        if (transactionBody.hasMemo()) {
            entity.setMemo(transactionBody.getMemo().getValue());
        }

        if (transactionBody.hasProxyAccountID()) {
            var proxyAccountId = EntityId.of(transactionBody.getProxyAccountID());
            entity.setProxyAccountId(proxyAccountId);
            recordItem.addEntityId(proxyAccountId);
        }

        if (transactionBody.hasReceiverSigRequiredWrapper()) {
            entity.setReceiverSigRequired(
                    transactionBody.getReceiverSigRequiredWrapper().getValue());
        } else if (transactionBody.getReceiverSigRequired()) {
            // support old transactions
            entity.setReceiverSigRequired(transactionBody.getReceiverSigRequired());
        }

        updateStakingInfo(recordItem, entity);
        entity.setType(EntityType.ACCOUNT);
        entityListener.onEntity(entity);
    }

    private void updateStakingInfo(RecordItem recordItem, Entity entity) {
        if (recordItem.getHapiVersion().isLessThan(HAPI_VERSION_0_27_0)) {
            return;
        }
        var transactionBody = recordItem.getTransactionBody().getCryptoUpdateAccount();

        if (transactionBody.hasDeclineReward()) {
            entity.setDeclineReward(transactionBody.getDeclineReward().getValue());
        }

        switch (transactionBody.getStakedIdCase()) {
            case STAKEDID_NOT_SET:
                break;
            case STAKED_NODE_ID:
                entity.setStakedNodeId(transactionBody.getStakedNodeId());
                entity.setStakedAccountId(AbstractEntity.ACCOUNT_ID_CLEARED);
                break;
            case STAKED_ACCOUNT_ID:
                var accountId = EntityId.of(transactionBody.getStakedAccountId());
                entity.setStakedAccountId(accountId.getId());
                entity.setStakedNodeId(AbstractEntity.NODE_ID_CLEARED);
                recordItem.addEntityId(accountId);
                break;
        }

        // If the stake node id or the decline reward value has changed, we start a new stake period.
        if (transactionBody.getStakedIdCase() != STAKEDID_NOT_SET || transactionBody.hasDeclineReward()) {
            entity.setStakePeriodStart(Utility.getEpochDay(recordItem.getConsensusTimestamp()));
        }
    }
}
