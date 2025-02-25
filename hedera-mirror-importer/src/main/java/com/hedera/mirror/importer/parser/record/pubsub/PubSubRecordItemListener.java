// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.pubsub;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.domain.PubSubMessage;
import com.hedera.mirror.importer.parser.record.RecordItemListener;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
@ConditionalOnPubSubRecordParser
public class PubSubRecordItemListener implements RecordItemListener {

    private final PubSubProperties pubSubProperties;
    private final PubSubTemplate pubSubTemplate;
    private final AddressBookService addressBookService;
    private final TransactionHandlerFactory transactionHandlerFactory;

    @Override
    public void onItem(RecordItem recordItem) throws ImporterException {
        TransactionBody body = recordItem.getTransactionBody();
        TransactionRecord txRecord = recordItem.getTransactionRecord();
        TransactionType transactionType = TransactionType.of(recordItem.getTransactionType());
        TransactionHandler transactionHandler = transactionHandlerFactory.get(transactionType);
        long consensusTimestamp = DomainUtils.timeStampInNanos(txRecord.getConsensusTimestamp());

        if (log.isTraceEnabled()) {
            log.trace("Storing transaction body: {}", Utility.printProtoMessage(body));
        }

        EntityId entityId;
        try {
            entityId = transactionHandler.getEntity(recordItem);
        } catch (InvalidEntityException e) { // transaction can have invalid topic/contract/file id
            if (!recordItem.isInvalidIdError()) {
                Utility.handleRecoverableError(
                        "Invalid entity encountered for consensusTimestamp {} : {}",
                        consensusTimestamp,
                        e.getMessage());
            }
            entityId = EntityId.EMPTY;
        }

        PubSubMessage pubSubMessage = buildPubSubMessage(consensusTimestamp, entityId, recordItem);
        Map<String, String> header = Map.of(
                "consensusTimestamp", pubSubMessage.getConsensusTimestamp().toString());
        try {
            sendPubSubMessage(pubSubMessage, header, 0);
        } catch (Exception e) {
            // This will make RecordFileParser to retry whole file, thus sending duplicates of previous transactions
            // in this file. In needed in future, this can be optimized to resend only the txns with consensusTimestamp
            // greater than that of last correctly sent txn.
            throw new ParserException("Error sending transaction to pubsub", e);
        }

        if (addressBookService.isAddressBook(entityId)) {
            FileID fileID = null;
            byte[] fileBytes = null;

            if (body.hasFileAppend()) {
                fileID = body.getFileAppend().getFileID();
                fileBytes = body.getFileAppend().getContents().toByteArray();
            } else if (body.hasFileCreate()) {
                fileID = txRecord.getReceipt().getFileID();
                fileBytes = body.getFileCreate().getContents().toByteArray();
            } else if (body.hasFileUpdate()) {
                fileID = body.getFileUpdate().getFileID();
                fileBytes = body.getFileUpdate().getContents().toByteArray();
            }

            FileData fileData =
                    new FileData(consensusTimestamp, fileBytes, EntityId.of(fileID), recordItem.getTransactionType());
            addressBookService.update(fileData);
        }
    }

    private void sendPubSubMessage(PubSubMessage message, Map<String, String> header, int retryCount) {
        var publishResult = pubSubTemplate.publish(pubSubProperties.getTopicName(), message, header);
        setPublishCallback(publishResult, message, header, retryCount);
    }

    private PubSubMessage buildPubSubMessage(long consensusTimestamp, EntityId entity, RecordItem recordItem) {
        var nonFeeTransfers = addNonFeeTransfers(recordItem.getTransactionBody());
        return new PubSubMessage(
                consensusTimestamp,
                entity,
                recordItem.getTransactionType(),
                new PubSubMessage.Transaction(recordItem.getTransactionBody(), recordItem.getSignatureMap()),
                recordItem.getTransactionRecord(),
                nonFeeTransfers);
    }

    /**
     * Set of explicit transfers in the transaction.
     */
    private Iterable<AccountAmount> addNonFeeTransfers(TransactionBody body) {
        var nonFeeTransfers = body.getCryptoTransfer().getTransfers().getAccountAmountsList();
        if (!nonFeeTransfers.iterator().hasNext()) { // return null if empty
            return null;
        }
        return nonFeeTransfers;
    }

    private void setPublishCallback(
            CompletableFuture<String> publishResult,
            PubSubMessage message,
            Map<String, String> header,
            int retryCount) {
        int retry = retryCount + 1;
        publishResult.whenComplete((result, ex) -> {
            if (ex != null) {
                if (retry > pubSubProperties.getMaxSendAttempts()) {
                    log.error("Failed to send message to PubSub after {} attempts: {}", retry - 1, ex);
                } else {
                    log.warn("Attempt {} to send message to PubSub failed: {}", retry, ex);
                    sendPubSubMessage(message, header, retry);
                }
            } else {
                log.debug("Published transaction : {}", message.getConsensusTimestamp());
            }
        });
    }
}
