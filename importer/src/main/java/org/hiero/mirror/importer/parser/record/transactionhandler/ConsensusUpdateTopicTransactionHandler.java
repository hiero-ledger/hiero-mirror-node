// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.FixedCustomFee;
import com.hederahashgraph.api.proto.java.Timestamp;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.common.domain.topic.Topic;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

@Named
class ConsensusUpdateTopicTransactionHandler extends AbstractEntityCrudTransactionHandler {

    ConsensusUpdateTopicTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityIdService, entityListener, TransactionType.CONSENSUSUPDATETOPIC);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(
                recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var transactionBody = recordItem.getTransactionBody().getConsensusUpdateTopic();

        if (transactionBody.hasAutoRenewAccount()) {
            // Allow clearing of the autoRenewAccount by allowing it to be set to 0
            entityIdService
                    .lookup(transactionBody.getAutoRenewAccount())
                    .ifPresentOrElse(
                            entityId -> {
                                entity.setAutoRenewAccountId(entityId.getId());
                                recordItem.addEntityId(entityId);
                            },
                            () -> Utility.handleRecoverableError(
                                    "Invalid autoRenewAccountId at {}", consensusTimestamp));
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasExpirationTime()) {
            Timestamp expirationTime = transactionBody.getExpirationTime();
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(expirationTime));
        }

        if (transactionBody.hasMemo()) {
            entity.setMemo(transactionBody.getMemo().getValue());
        }

        entity.setType(EntityType.TOPIC);
        entityListener.onEntity(entity);

        updateTopic(consensusTimestamp, entity.getId(), transactionBody);

        if (transactionBody.hasCustomFees()) {
            updateCustomFee(transactionBody.getCustomFees().getFeesList(), recordItem, entity.getId());
        }
    }

    void updateCustomFee(List<FixedCustomFee> fixedCustomFees, RecordItem recordItem, long topicId) {
        var fixedFees = new ArrayList<FixedFee>();
        for (var fixedCustomFee : fixedCustomFees) {
            var collector = EntityId.of(fixedCustomFee.getFeeCollectorAccountId());
            var fixedFee = fixedCustomFee.getFixedFee();
            var tokenId = fixedFee.hasDenominatingTokenId() ? EntityId.of(fixedFee.getDenominatingTokenId()) : null;
            fixedFees.add(FixedFee.builder()
                    .amount(fixedFee.getAmount())
                    .collectorAccountId(collector)
                    .denominatingTokenId(tokenId)
                    .build());
            recordItem.addEntityId(collector);
            recordItem.addEntityId(tokenId);
        }

        var customFee = CustomFee.builder()
                .entityId(topicId)
                .fixedFees(fixedFees)
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();
        entityListener.onCustomFee(customFee);
    }

    private void updateTopic(
            long consensusTimestamp, long topicId, ConsensusUpdateTopicTransactionBody transactionBody) {
        var adminKey =
                transactionBody.hasAdminKey() ? transactionBody.getAdminKey().toByteArray() : null;
        // The fee exempt key list is not cleared in the database if it's an empty list, instead, importer would
        // serialize the protobuf message with an empty key list. The reader should understand that semantically
        // an empty list is the same as no fee exempt key list.
        var feeExemptKeyList = transactionBody.hasFeeExemptKeyList()
                ? transactionBody.getFeeExemptKeyList().toByteArray()
                : null;
        var feeScheduleKey = transactionBody.hasFeeScheduleKey()
                ? transactionBody.getFeeScheduleKey().toByteArray()
                : null;
        var submitKey =
                transactionBody.hasSubmitKey() ? transactionBody.getSubmitKey().toByteArray() : null;
        var topic = Topic.builder()
                .adminKey(adminKey)
                .id(topicId)
                .feeExemptKeyList(feeExemptKeyList)
                .feeScheduleKey(feeScheduleKey)
                .submitKey(submitKey)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onTopic(topic);
    }
}
