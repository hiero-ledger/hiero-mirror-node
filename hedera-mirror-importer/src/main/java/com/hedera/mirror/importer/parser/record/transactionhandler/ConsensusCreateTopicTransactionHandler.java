// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.topic.Topic;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.FeeExemptKeyList;
import jakarta.inject.Named;

@Named
class ConsensusCreateTopicTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private final ConsensusUpdateTopicTransactionHandler consensusUpdateTopicTransactionHandler;

    ConsensusCreateTopicTransactionHandler(
            EntityIdService entityIdService,
            EntityListener entityListener,
            ConsensusUpdateTopicTransactionHandler consensusUpdateTopicTransactionHandler) {
        super(entityIdService, entityListener, TransactionType.CONSENSUSCREATETOPIC);
        this.consensusUpdateTopicTransactionHandler = consensusUpdateTopicTransactionHandler;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionRecord().getReceipt().getTopicID());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getConsensusCreateTopic();

        if (transactionBody.hasAutoRenewAccount()) {
            var autoRenewAccountId = EntityId.of(transactionBody.getAutoRenewAccount());
            entity.setAutoRenewAccountId(autoRenewAccountId.getId());
            recordItem.addEntityId(autoRenewAccountId);
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        entity.setMemo(transactionBody.getMemo());
        entity.setType(EntityType.TOPIC);
        entityListener.onEntity(entity);

        createTopic(recordItem.getConsensusTimestamp(), entity.getId(), transactionBody);
        consensusUpdateTopicTransactionHandler.updateCustomFee(
                transactionBody.getCustomFeesList(), recordItem, entity.getId());
    }

    private void createTopic(
            long consensusTimestamp, long topicId, ConsensusCreateTopicTransactionBody transactionBody) {
        var adminKey =
                transactionBody.hasAdminKey() ? transactionBody.getAdminKey().toByteArray() : null;
        // Treat fee_exempt_key_list not set the same as an empty list so it's consistent with consensus update
        // topic transaction handler
        var feeExemptKeyList = FeeExemptKeyList.newBuilder()
                .addAllKeys(transactionBody.getFeeExemptKeyListList())
                .build()
                .toByteArray();
        var feeScheduleKey = transactionBody.hasFeeScheduleKey()
                ? transactionBody.getFeeScheduleKey().toByteArray()
                : null;
        var submitKey =
                transactionBody.hasSubmitKey() ? transactionBody.getSubmitKey().toByteArray() : null;
        var topic = Topic.builder()
                .adminKey(adminKey)
                .createdTimestamp(consensusTimestamp)
                .id(topicId)
                .feeExemptKeyList(feeExemptKeyList)
                .feeScheduleKey(feeScheduleKey)
                .submitKey(submitKey)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onTopic(topic);
    }
}
