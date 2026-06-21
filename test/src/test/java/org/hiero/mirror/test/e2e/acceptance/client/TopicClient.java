// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.google.common.primitives.Longs;
import com.hedera.hashgraph.sdk.CustomFeeLimit;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.Key;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicDeleteTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.TopicUpdateTransaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.springframework.core.retry.RetryTemplate;

@Named
public class TopicClient extends AbstractNetworkClient {

    public static final Duration autoRenewPeriod = Duration.ofSeconds(8000000);

    private final Map<Long, Instant> recordPublishInstants;
    private final Collection<TopicId> topicIds = new CopyOnWriteArrayList<>();

    public TopicClient(
            SDKClient sdkClient, RetryTemplate retryTemplate, AcceptanceTestProperties acceptanceTestProperties) {
        super(sdkClient, retryTemplate, acceptanceTestProperties);
        recordPublishInstants = new ConcurrentHashMap<>();
    }

    @Override
    public void clean() {
        log.info("Deleting {} topics", topicIds.size());
        deleteOrLogEntities(topicIds, this::deleteTopic);
    }

    @Override
    protected void logEntities() {
        for (var topicId : topicIds) {
            // Log the values so that they can be parsed in CI and passed to the k6 tests as input.
            System.out.println("DEFAULT_TOPIC=" + topicId.num);
        }
    }

    public NetworkTransactionResponse createTopic(ExpandedAccountId adminAccount, PublicKey submitKey) {
        String memo = getMemo("Create Topic");
        TopicCreateTransaction consensusTopicCreateTransaction = new TopicCreateTransaction()
                .setAdminKey(adminAccount.getPublicKey())
                .setAutoRenewAccountId(sdkClient.getExpandedOperatorAccountId().getAccountId())
                .setTopicMemo(memo)
                .setTransactionMemo(memo)
                .setAutoRenewPeriod(autoRenewPeriod); // INSUFFICIENT_TX_FEE, also unsupported

        if (submitKey != null) {
            consensusTopicCreateTransaction.setSubmitKey(submitKey);
        }

        var keyList = KeyList.of(adminAccount.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(consensusTopicCreateTransaction, keyList);
        var topicId = response.getReceipt().topicId;
        log.info(
                "Created new topic {} with memo '{}' via {} in {}",
                topicId,
                memo,
                response.getTransactionId(),
                response.getStopwatch());
        topicIds.add(topicId);
        return response;
    }

    public NetworkTransactionResponse createTopicWithCustomFees(
            ExpandedAccountId adminAccount,
            Key submitKey,
            Key feeScheduleKey,
            List<CustomFixedFee> customFixedFeeList,
            List<Key> feeExemptKeys) {
        String memo = getMemo("Create Topic With Custom Fees");
        TopicCreateTransaction consensusTopicCreateTransaction = new TopicCreateTransaction()
                .setAdminKey(adminAccount.getPublicKey())
                .setAutoRenewAccountId(sdkClient.getExpandedOperatorAccountId().getAccountId())
                .setTopicMemo(memo)
                .setTransactionMemo(memo)
                .setCustomFees(customFixedFeeList)
                .setFeeScheduleKey(feeScheduleKey)
                .setFeeExemptKeys(feeExemptKeys)
                .setAutoRenewPeriod(autoRenewPeriod);

        if (submitKey != null) {
            consensusTopicCreateTransaction.setSubmitKey(submitKey);
        }

        var keyList = KeyList.of(adminAccount.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(consensusTopicCreateTransaction, keyList);
        var topicId = response.getReceipt().topicId;
        log.info(
                "Created new topic {} with memo '{}' via {} in {}",
                topicId,
                memo,
                response.getTransactionId(),
                response.getStopwatch());
        topicIds.add(topicId);
        return response;
    }

    public NetworkTransactionResponse updateTopic(TopicId topicId, Key feeSchedulekey) {
        String memo = getMemo("Update Topic");
        TopicUpdateTransaction consensusTopicUpdateTransaction = new TopicUpdateTransaction()
                .setTopicId(topicId)
                .setTopicMemo(memo)
                .setAutoRenewPeriod(autoRenewPeriod)
                .clearAutoRenewAccountId()
                .setTransactionMemo(memo)
                .clearFeeScheduleKey()
                .clearFeeExemptKeys()
                .clearCustomFees();

        var keyList = KeyList.of(feeSchedulekey);
        var response = executeTransactionAndRetrieveReceipt(consensusTopicUpdateTransaction, keyList);
        log.info(
                "Updated topic {} with memo '{}' via {} in {}",
                topicId,
                memo,
                response.getTransactionId(),
                response.getStopwatch());
        return response;
    }

    public NetworkTransactionResponse deleteTopic(TopicId topicId) {
        TopicDeleteTransaction consensusTopicDeleteTransaction =
                new TopicDeleteTransaction().setTopicId(topicId).setTransactionMemo(getMemo("Delete Topic"));

        var response = executeTransactionAndRetrieveReceipt(consensusTopicDeleteTransaction);
        log.info("Deleted topic {} via {} in {}", topicId, response.getTransactionId(), response.getStopwatch());
        topicIds.remove(topicId);
        return response;
    }

    public NetworkTransactionResponse publishMessageToTopicWithFixedFee(
            TopicId topicId, String message, KeyList submitKeys, ExpandedAccountId payer, CustomFeeLimit feeLimit) {
        TopicMessageSubmitTransaction consensusMessageSubmitTransaction = new TopicMessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage(message)
                .setTransactionMemo(getMemo("Publish topic message to topic with fixed fee"));
        if (feeLimit != null) {
            consensusMessageSubmitTransaction.addCustomFeeLimit(feeLimit);
        }

        final var response = payer == null
                ? executeTransactionAndRetrieveReceipt(consensusMessageSubmitTransaction, submitKeys)
                : executeTransactionAndRetrieveReceipt(consensusMessageSubmitTransaction, submitKeys, payer);
        log.info(
                "Published message '{}' to topic {} with fee limit via {} in {}",
                message,
                topicId,
                response.getTransactionId(),
                response.getStopwatch());
        return response;
    }

    public List<TransactionReceipt> publishMessagesToTopic(
            TopicId topicId, String baseMessage, KeyList submitKeys, int numMessages, boolean verify) {
        log.debug("Publishing {} message(s) to topicId : {}.", numMessages, topicId);
        List<TransactionReceipt> transactionReceiptList = new ArrayList<>();
        for (int i = 0; i < numMessages; i++) {
            byte[] publishTimestampByteArray = Longs.toByteArray(System.currentTimeMillis());
            byte[] suffixByteArray = ("_" + baseMessage + "_" + (i + 1)).getBytes(StandardCharsets.UTF_8);
            byte[] message = ArrayUtils.addAll(publishTimestampByteArray, suffixByteArray);

            if (verify) {
                transactionReceiptList.add(publishMessageToTopicAndVerify(topicId, message, submitKeys));
            } else {
                publishMessageToTopic(topicId, message, submitKeys);
            }
        }

        return transactionReceiptList;
    }

    public TransactionId publishMessageToTopic(TopicId topicId, byte[] message, KeyList submitKeys) {
        TopicMessageSubmitTransaction consensusMessageSubmitTransaction = new TopicMessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage(message)
                .setTransactionMemo(getMemo("Publish topic message"));

        final var response = executeTransactionAndRetrieveReceipt(consensusMessageSubmitTransaction, submitKeys);

        // get only the 1st sequence number
        if (recordPublishInstants.size() == 0) {
            final var transactionRecord = getTransactionRecord(response.getTransactionId());
            recordPublishInstants.put(0L, transactionRecord.consensusTimestamp);
        }

        log.info(
                "Published message '{}' to topic {} via {} in {}",
                new String(message, StandardCharsets.UTF_8),
                topicId,
                response.getTransactionId(),
                response.getStopwatch());

        return response.getTransactionId();
    }

    public TransactionReceipt publishMessageToTopicAndVerify(TopicId topicId, byte[] message, KeyList submitKeys) {
        TransactionId transactionId = publishMessageToTopic(topicId, message, submitKeys);
        TransactionReceipt transactionReceipt = null;
        try {
            final var transactionRecord = getTransactionRecord(transactionId);
            transactionReceipt = transactionRecord.receipt;

            // note time stamp
            recordPublishInstants.put(transactionReceipt.topicSequenceNumber, transactionRecord.consensusTimestamp);
        } catch (Exception e) {
            log.error("Error retrieving transaction receipt", e);
        }

        log.trace(
                "Verified message published : '{}' to topicId : {} with sequence number : {}",
                message,
                topicId,
                transactionReceipt.topicSequenceNumber);

        return transactionReceipt;
    }

    public Instant getInstantOfPublishedMessage(long sequenceNumber) {
        return recordPublishInstants.get(sequenceNumber);
    }

    public Instant getInstantOfFirstPublishedMessage() {
        return recordPublishInstants.get(0L);
    }
}
