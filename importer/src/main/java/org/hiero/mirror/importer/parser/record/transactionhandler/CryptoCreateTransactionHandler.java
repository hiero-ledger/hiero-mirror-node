// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.common.domain.transaction.RecordFile.HAPI_VERSION_0_27_0;
import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;

import com.google.protobuf.ByteString;
import jakarta.inject.Named;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

@Named
class CryptoCreateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private final EVMHookHandler evmHookHandler;

    CryptoCreateTransactionHandler(
            EntityIdService entityIdService, EntityListener entityListener, EVMHookHandler evmHookHandler) {
        super(entityIdService, entityListener, TransactionType.CRYPTOCREATEACCOUNT);
        this.evmHookHandler = evmHookHandler;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        transaction.setInitialBalance(
                recordItem.getTransactionBody().getCryptoCreateAccount().getInitialBalance());
    }

    @Override
    @SuppressWarnings({"deprecation", "java:S1874"})
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody().getCryptoCreateAccount();
        var alias = DomainUtils.toBytes(
                transactionRecord.getAlias() != ByteString.EMPTY
                        ? transactionRecord.getAlias()
                        : transactionBody.getAlias());
        boolean emptyAlias = ArrayUtils.isEmpty(alias);
        var key = transactionBody.hasKey() ? transactionBody.getKey().toByteArray() : null;
        boolean emptyKey = ArrayUtils.isEmpty(key);
        entity.setType(EntityType.ACCOUNT);

        if (!emptyAlias) {
            entity.setAlias(alias);
            if (emptyKey && alias.length > EVM_ADDRESS_LENGTH) {
                entity.setKey(alias);
            }
        }

        if (!emptyKey) {
            entity.setKey(key);
        }

        var evmAddress = transactionRecord.getEvmAddress();
        if (evmAddress != ByteString.EMPTY) {
            entity.setEvmAddress(DomainUtils.toBytes(evmAddress));
        } else if (!emptyAlias) {
            entity.setEvmAddress(Utility.aliasToEvmAddress(alias));
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasProxyAccountID()) {
            var proxyAccountId = EntityId.of(transactionBody.getProxyAccountID());
            entity.setProxyAccountId(proxyAccountId);
            recordItem.addEntityId(proxyAccountId);
        }

        entity.setBalance(0L);
        entity.setBalanceTimestamp(recordItem.getConsensusTimestamp());
        entity.setMaxAutomaticTokenAssociations(transactionBody.getMaxAutomaticTokenAssociations());
        entity.setMemo(transactionBody.getMemo());
        entity.setReceiverSigRequired(transactionBody.getReceiverSigRequired());

        updateStakingInfo(recordItem, entity);
        entityListener.onEntity(entity);
        evmHookHandler.processHookCreationDetails(
                recordItem, entity.toEntityId(), transactionBody.getHookCreationDetailsList());
    }

    private void updateStakingInfo(RecordItem recordItem, Entity entity) {
        if (recordItem.getHapiVersion().isLessThan(HAPI_VERSION_0_27_0)) {
            return;
        }
        var transactionBody = recordItem.getTransactionBody().getCryptoCreateAccount();
        entity.setDeclineReward(transactionBody.getDeclineReward());

        switch (transactionBody.getStakedIdCase()) {
            case STAKEDID_NOT_SET -> {
                return;
            }
            case STAKED_NODE_ID -> entity.setStakedNodeId(transactionBody.getStakedNodeId());
            case STAKED_ACCOUNT_ID -> {
                var accountId = EntityId.of(transactionBody.getStakedAccountId());
                entity.setStakedAccountId(accountId.getId());
                recordItem.addEntityId(accountId);
            }
        }

        entity.setStakePeriodStart(Utility.getEpochDay(recordItem.getConsensusTimestamp()));
    }
}
