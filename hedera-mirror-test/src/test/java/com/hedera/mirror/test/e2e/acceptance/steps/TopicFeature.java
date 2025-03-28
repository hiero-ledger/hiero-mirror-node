// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.CustomFeeLimit;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.Key;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.rest.model.Key.TypeEnum;
import com.hedera.mirror.rest.model.Topic;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.SDKClient;
import com.hedera.mirror.test.e2e.acceptance.client.SubscriptionResponse;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.util.FeatureInputHandler;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.grpc.StatusRuntimeException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

@CustomLog
@RequiredArgsConstructor
public class TopicFeature extends AbstractFeature {

    private final long FIXED_FEE_AMOUNT = 10;
    private final String TOPIC_MESSAGE = "Fixed fee topic message";

    private final AcceptanceTestProperties acceptanceProps;
    private final MirrorNodeClient mirrorClient;
    private final SDKClient sdkClient;
    private final TopicClient topicClient;
    private final CommonProperties commonProperties;
    private final TokenClient tokenClient;
    private final AccountClient accountClient;

    private int messageSubscribeCount;
    private long latency;
    private TopicMessageQuery topicMessageQuery;
    private TopicId consensusTopicId;
    private SubscriptionResponse subscriptionResponse;
    private PrivateKey privateKey;
    private Instant testInstantReference;
    private List<TransactionReceipt> publishedTransactionReceipts;
    private boolean deleted = false;
    private TokenId fungibleToken;

    @Given("I successfully create a new topic id")
    public void createNewTopic() {
        testInstantReference = Instant.now();
        PublicKey submitPublicKey = privateKey.getPublicKey();
        log.trace("Topic creation PrivateKey : {}, PublicKey : {}", privateKey, submitPublicKey);

        networkTransactionResponse =
                topicClient.createTopic(topicClient.getSdkClient().getExpandedOperatorAccountId(), submitPublicKey);
        assertNotNull(networkTransactionResponse.getReceipt());
        TopicId topicId = networkTransactionResponse.getReceipt().topicId;
        assertNotNull(topicId);

        consensusTopicId = topicId;
        topicMessageQuery = new TopicMessageQuery().setTopicId(consensusTopicId).setStartTime(Instant.EPOCH);
    }

    @Given("I create Fungible token and a key")
    public void createFungibleTokenAndKey() {
        // Get Fungible token to be used as a denominating token for custom fees
        var fungibleTokenResponse = tokenClient.getToken(TokenNameEnum.TOPIC_CUSTOM_FEE_FUNGIBLE);
        fungibleToken = fungibleTokenResponse.tokenId();
        networkTransactionResponse = fungibleTokenResponse.response();
        privateKey = PrivateKey.generateED25519();
    }

    @Given(
            "I successfully create a new topic with fixed HTS and HBAR fee. {account} is collector and {account} is exempt")
    public void createNewTopicWithFixedHTSFee(AccountNameEnum collectorAccountName, AccountNameEnum exemptAccountName) {
        var collectorAccount = accountClient.getAccount(collectorAccountName);
        var exemptAccount = accountClient.getAccount(exemptAccountName);

        testInstantReference = Instant.now();
        PublicKey submitPublicKey = privateKey.getPublicKey();

        log.trace("Topic creation PrivateKey : {}, PublicKey : {}", privateKey, submitPublicKey);

        // Create new HBAR and HTS fixed fees
        var fixedHTSFee = new CustomFixedFee()
                .setAmount(FIXED_FEE_AMOUNT)
                .setFeeCollectorAccountId(collectorAccount.getAccountId())
                .setDenominatingTokenId(fungibleToken);
        var fixedHBARFee = new CustomFixedFee()
                .setHbarAmount(Hbar.fromTinybars(FIXED_FEE_AMOUNT))
                .setFeeCollectorAccountId(collectorAccount.getAccountId());

        // Create a list of Fixed Fees
        List<CustomFixedFee> listOfFixedFees = new ArrayList<>();
        listOfFixedFees.add(fixedHTSFee);
        listOfFixedFees.add(fixedHBARFee);
        // Add account to exempt list
        List<Key> listOfExemptKeys = new ArrayList<>();
        listOfExemptKeys.add(exemptAccount.getPublicKey());
        // Create Topic with custom fixed fees
        networkTransactionResponse = topicClient.createTopicWithCustomFees(
                topicClient.getSdkClient().getExpandedOperatorAccountId(),
                submitPublicKey,
                submitPublicKey,
                listOfFixedFees,
                listOfExemptKeys);
        assertNotNull(networkTransactionResponse.getReceipt());
        TopicId topicId = networkTransactionResponse.getReceipt().topicId;

        assertThat(topicId).isNotNull();
        var getTopicResponse = mirrorClient.getTopic(topicId.toString());
        assertThat(getTopicResponse.getFeeScheduleKey()).isNotNull();
        assertThat(submitPublicKey.toString())
                .contains(getTopicResponse.getFeeScheduleKey().getKey());
        assertThat(listOfExemptKeys.getFirst().toString())
                .contains(getTopicResponse.getFeeExemptKeyList().getFirst().getKey());

        // Assert fixed fees
        assertThat(mirrorClient.getTopic(topicId.toString()).getCustomFees().getFixedFees())
                .hasSize(2);
        var fixedFeesResponse = getTopicResponse.getCustomFees().getFixedFees();
        assertThat(fixedFeesResponse).isNotNull();
        fixedFeesResponse.stream()
                .peek(fee -> {
                    assertThat(fee.getAmount()).isEqualTo(FIXED_FEE_AMOUNT);
                    assertThat(fee.getCollectorAccountId())
                            .isEqualTo(collectorAccount.getAccountId().toString());
                })
                .filter(fee -> fee.getDenominatingTokenId() != null)
                .forEach(fee -> assertThat(fee.getDenominatingTokenId()).isEqualTo(fungibleToken.toString()));

        assertNotNull(topicId);
        consensusTopicId = topicId;
        topicMessageQuery = new TopicMessageQuery().setTopicId(consensusTopicId).setStartTime(Instant.EPOCH);
    }

    @Given("I successfully create a new open topic")
    public void createNewOpenTopic() {
        testInstantReference = Instant.now();

        networkTransactionResponse =
                topicClient.createTopic(topicClient.getSdkClient().getExpandedOperatorAccountId(), null);
        assertNotNull(networkTransactionResponse.getReceipt());
        TopicId topicId = networkTransactionResponse.getReceipt().topicId;
        assertNotNull(topicId);

        consensusTopicId = topicId;
        topicMessageQuery = new TopicMessageQuery().setTopicId(consensusTopicId).setStartTime(Instant.EPOCH);
    }

    @When("I successfully update an existing topic")
    public void updateTopic() {
        networkTransactionResponse = topicClient.updateTopic(consensusTopicId);
        assertNotNull(networkTransactionResponse.getReceipt());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @When("I successfully delete the topic")
    public void deleteTopic() {
        networkTransactionResponse = topicClient.deleteTopic(consensusTopicId);
        assertNotNull(networkTransactionResponse.getReceipt());
        deleted = true;
    }

    @Then("the mirror node should retrieve the topic")
    public void verifyTopic() {
        var topicId = consensusTopicId.toString();
        var topic = mirrorClient.getTopic(topicId);
        var operator = sdkClient.getExpandedOperatorAccountId();

        assertThat(topic)
                .isNotNull()
                .returns(operator.getPublicKey().toStringRaw(), t -> t.getAdminKey()
                        .getKey())
                .returns(TypeEnum.ED25519, t -> topic.getAdminKey().getType())
                .returns(TopicClient.autoRenewPeriod.getSeconds(), Topic::getAutoRenewPeriod)
                .returns(deleted, Topic::getDeleted)
                .returns(topicId, Topic::getTopicId)
                .satisfies(t -> assertThat(t.getTimestamp().getFrom()).isNotEmpty())
                .satisfies(t -> assertThat(t.getCreatedTimestamp()).isNotEmpty())
                .satisfies(t -> assertThat(t.getMemo()).isNotEmpty());
    }

    @And("the mirror node should successfully observe the transaction")
    public void verifyMirrorTransactionSuccessful() {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        var transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        var mirrorTransaction = transactions.get(0);

        assertThat(mirrorTransaction.getConsensusTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getName()).isNotNull();
        assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        assertThat(mirrorTransaction.getValidStartTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
    }

    @Given("I provide a topic num {string}")
    public void setTopicIdParam(String topicNum) {
        testInstantReference = Instant.now();
        topicMessageQuery = new TopicMessageQuery().setStartTime(Instant.EPOCH);
        consensusTopicId = null;

        if (!topicNum.isEmpty()) {
            consensusTopicId =
                    new TopicId(commonProperties.getShard(), commonProperties.getRealm(), Long.parseLong(topicNum));
            topicMessageQuery.setTopicId(consensusTopicId);
        }
        messageSubscribeCount = 0;
    }

    @Given("I provide a number of messages {int} I want to receive")
    public void setTopicListenParams(int numMessages) {
        messageSubscribeCount = numMessages;
    }

    @Given("I provide a number of messages {int} I want to receive within {int} seconds")
    public void setTopicListenParams(int numMessages, int latency) {
        messageSubscribeCount = numMessages;
        this.latency = latency;
    }

    @Given("I provide a {int} in seconds of which I want to receive messages within")
    public void setSubscribeParams(int latency) {
        this.latency = latency;
    }

    @Given("I provide a starting timestamp {string} and a number of messages {int} I want to receive")
    public void setTopicListenParams(String startTimestamp, int numMessages) {
        messageSubscribeCount = numMessages;

        Instant startTime = FeatureInputHandler.messageQueryDateStringToInstant(startTimestamp, testInstantReference);
        topicMessageQuery.setStartTime(startTime);
    }

    @Given("I provide a starting timestamp {string} and ending timestamp {string} and a number of messages {int} I "
            + "want to receive")
    public void setTopicListenParams(String startTimestamp, String endTimestamp, int numMessages) {
        messageSubscribeCount = numMessages;

        Instant startTime = FeatureInputHandler.messageQueryDateStringToInstant(startTimestamp, testInstantReference);
        Instant endTime = FeatureInputHandler.messageQueryDateStringToInstant(endTimestamp, Instant.now());

        topicMessageQuery.setStartTime(startTime).setEndTime(endTime);
    }

    @Given("I provide a startSequence {int} and endSequence {int} and a number of messages {int} I want to receive")
    public void setTopicListenParams(int startSequence, int endSequence, int numMessages) {
        messageSubscribeCount = numMessages;

        Instant startTime =
                topicClient.getInstantOfPublishedMessage(startSequence - 1).minusMillis(10);
        Instant endTime =
                topicClient.getInstantOfPublishedMessage(endSequence - 1).plusMillis(10);
        topicMessageQuery.setStartTime(startTime).setEndTime(endTime);
    }

    @Given("I provide a starting timestamp {string} and ending timestamp {string} and a limit of {int} messages I "
            + "want to receive")
    public void setTopicListenParamswLimit(String startTimestamp, String endTimestamp, int limit) {
        messageSubscribeCount = limit;

        Instant startTime = FeatureInputHandler.messageQueryDateStringToInstant(startTimestamp);
        Instant endTime = FeatureInputHandler.messageQueryDateStringToInstant(endTimestamp);
        topicMessageQuery.setStartTime(startTime).setEndTime(endTime).setLimit(limit);
    }

    @When("I subscribe to the topic")
    public void verifySubscriptionChannelConnection() throws Throwable {
        subscriptionResponse = mirrorClient.subscribeToTopic(sdkClient, topicMessageQuery);
        assertNotNull(subscriptionResponse);
    }

    @When("I publish {int} batches of {int} messages every {long} milliseconds")
    @Retryable(
            retryFor = {PrecheckStatusException.class, ReceiptStatusException.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    @SuppressWarnings("java:S2925")
    public void publishTopicMessages(int numGroups, int messageCount, long milliSleep) throws InterruptedException {
        for (int i = 0; i < numGroups; i++) {
            Thread.sleep(milliSleep, 0);
            publishTopicMessages(messageCount);
            log.trace(
                    "Emitted {} message(s) in batch {} of {} potential batches. Sleeping {} ms",
                    messageCount,
                    i + 1,
                    numGroups,
                    milliSleep);
        }

        messageSubscribeCount = numGroups * messageCount;
    }

    @Retryable(
            retryFor = {PrecheckStatusException.class, ReceiptStatusException.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    public void publishTopicMessages(int messageCount) {
        messageSubscribeCount = messageCount;
        topicClient.publishMessagesToTopic(consensusTopicId, "New message", getKeys(), messageCount, false);
    }

    @When("I publish and verify {int} messages sent")
    @Retryable(
            retryFor = {AssertionError.class, PrecheckStatusException.class, ReceiptStatusException.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    public void publishAndVerifyTopicMessages(int messageCount) {
        messageSubscribeCount = messageCount;
        publishedTransactionReceipts =
                topicClient.publishMessagesToTopic(consensusTopicId, "New message", getKeys(), messageCount, true);
        assertEquals(messageCount, publishedTransactionReceipts.size());
    }

    @Then("I associate {account} as payer, {account} as collector and {account} as exempt with fungible token")
    public void associateAccountsAndTransferFunds(
            AccountNameEnum payerAccountName, AccountNameEnum collectorAccountName, AccountNameEnum exemptAccountName) {
        var payerAccount = accountClient.getAccount(payerAccountName);
        var collectorAccount = accountClient.getAccount(collectorAccountName);
        var exemptAccount = accountClient.getAccount(exemptAccountName);

        networkTransactionResponse = tokenClient.associate(payerAccount, fungibleToken);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        networkTransactionResponse = tokenClient.associate(collectorAccount, fungibleToken);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        networkTransactionResponse = tokenClient.associate(exemptAccount, fungibleToken);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        // Transfer funds to payer account
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleToken,
                sdkClient.getExpandedOperatorAccountId(),
                payerAccount.getAccountId(),
                payerAccount.getPrivateKey(),
                1000L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        // Transfer funds to exempt account
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleToken,
                sdkClient.getExpandedOperatorAccountId(),
                exemptAccount.getAccountId(),
                exemptAccount.getPrivateKey(),
                1000L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When(
            "{account} is exempt - {string}, publishes message to topic with fixed fee. {account} is a fixed fees collector")
    @Retryable(
            retryFor = {AssertionError.class, PrecheckStatusException.class, ReceiptStatusException.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    public void submitTopicMessage(
            AccountNameEnum payerAccountName, String isExempt, AccountNameEnum colleectorAccountName) {
        var payerAccount = accountClient.getAccount(payerAccountName);
        var collectorAccount = accountClient.getAccount(colleectorAccountName);

        // Create a list of Max Fixed Fees
        var maxCustomHTSFee = new CustomFixedFee();
        maxCustomHTSFee.setAmount(FIXED_FEE_AMOUNT + 1);
        maxCustomHTSFee.setDenominatingTokenId(fungibleToken);

        var maxCustomHbarFee = new CustomFixedFee();
        maxCustomHbarFee.setHbarAmount(Hbar.fromTinybars(FIXED_FEE_AMOUNT + 1));

        ArrayList<CustomFixedFee> listOfMaxFixedFees = new ArrayList<>();
        listOfMaxFixedFees.add(maxCustomHTSFee);
        listOfMaxFixedFees.add(maxCustomHbarFee);

        var customFeeLimit = new CustomFeeLimit();
        customFeeLimit.setPayerId(payerAccount.getAccountId()).setCustomFees(listOfMaxFixedFees);

        var payerAccountInitialHTSBalance = getTokenBalance(payerAccount.getAccountId(), fungibleToken);
        var collectorAccountInitialHTSBalance = getTokenBalance(collectorAccount.getAccountId(), fungibleToken);
        var collectorAccountInitialHBARBalance = accountClient.getBalance(collectorAccount);

        networkTransactionResponse = topicClient.publishMessageToTopicWithFixedFee(
                consensusTopicId, TOPIC_MESSAGE, getKeys(), payerAccount, customFeeLimit);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        // Verify max custom fees
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var getTransactionResponse = mirrorClient
                .getTransactions(transactionId)
                .getTransactions()
                .getFirst()
                .getMaxCustomFees();
        getTransactionResponse.stream()
                .peek(fee -> {
                    assertThat(fee.getAmount()).isEqualTo(FIXED_FEE_AMOUNT + 1);
                    assertThat(fee.getAccountId())
                            .isEqualTo(payerAccount.getAccountId().toString());
                })
                .filter(fee -> fee.getDenominatingTokenId() != null)
                .forEach(fee -> assertThat(fee.getDenominatingTokenId()).isEqualTo(fungibleToken.toString()));

        var payerAccountHTSBalance = getTokenBalance(payerAccount.getAccountId(), fungibleToken);
        var collectorAccountHTSBalance = getTokenBalance(collectorAccount.getAccountId(), fungibleToken);
        var collectorAccountHBARBalance = accountClient.getBalance(collectorAccount);

        if (Boolean.parseBoolean(isExempt)) {
            assertThat(payerAccountHTSBalance).isEqualTo(payerAccountInitialHTSBalance);
            assertThat(collectorAccountHTSBalance).isEqualTo(collectorAccountInitialHTSBalance);
            assertThat(collectorAccountHBARBalance).isEqualTo(collectorAccountInitialHBARBalance);
        } else {
            assertThat(payerAccountHTSBalance).isEqualTo(payerAccountInitialHTSBalance - FIXED_FEE_AMOUNT);
            assertThat(collectorAccountHTSBalance).isEqualTo(collectorAccountInitialHTSBalance + FIXED_FEE_AMOUNT);
            assertThat(collectorAccountHBARBalance).isEqualTo(collectorAccountInitialHBARBalance + FIXED_FEE_AMOUNT);
        }
    }

    @Then("I verify the published message from {account} in mirror node REST API")
    public void verifyTopicMessage(AccountNameEnum accountName) {
        var payerAccount = accountClient.getAccount(accountName);
        var getTopicMessageResponse =
                mirrorClient.getTopicMessage(consensusTopicId.toString()).getMessages();
        assertThat(getTopicMessageResponse).isNotNull();
        String base64EncodedMessage =
                Base64.getEncoder().encodeToString(TOPIC_MESSAGE.getBytes(StandardCharsets.UTF_8));
        // Filter the messages that were published by the payer account
        var topicMessage = getTopicMessageResponse.stream()
                .filter(message -> {
                    assert message.getPayerAccountId() != null;
                    return message.getPayerAccountId()
                            .equals(payerAccount.getAccountId().toString());
                })
                .findFirst();
        // Verify the message
        assertThat(topicMessage).isPresent().hasValueSatisfying(message -> {
            assertThat(message.getMessage()).isEqualTo(base64EncodedMessage);
        });
    }

    @Then("I unsubscribe from a topic")
    public void verifyUnSubscribeFromChannelConnection() {
        mirrorClient.unSubscribeFromTopic(subscriptionResponse.getSubscription());
    }

    @Then("the network should successfully establish a channel to this topic")
    public void verifySubscribeAndUnsubscribeChannelConnection() throws Throwable {
        verifySubscriptionChannelConnection();

        verifyUnSubscribeFromChannelConnection();
    }

    @Then("the network should observe an error {string}")
    public void verifySubscribeAndUnsubscribeChannelConnection(String errorCode) throws Throwable {
        assertThatThrownBy(this::verifySubscriptionChannelConnection)
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining(errorCode);
    }

    @Then("I subscribe with a filter to retrieve messages")
    @RetryAsserts
    public void retrieveTopicMessages() throws Throwable {
        assertNotNull(consensusTopicId, "consensusTopicId null");
        assertNotNull(topicMessageQuery, "TopicMessageQuery null");

        subscriptionResponse = subscribeWithBackgroundMessageEmission();
    }

    @Then("I subscribe with a filter to retrieve these published messages")
    @RetryAsserts
    public void retrievePublishedTopicMessages() throws Throwable {
        assertNotNull(consensusTopicId, "consensusTopicId null");
        assertNotNull(topicMessageQuery, "TopicMessageQuery null");

        // get start time from first published messages
        Instant startTime;
        if (publishedTransactionReceipts == null) {
            startTime = topicClient.getInstantOfFirstPublishedMessage();
        } else {
            long firstMessageSeqNum = publishedTransactionReceipts.get(0).topicSequenceNumber;
            startTime = topicClient.getInstantOfPublishedMessage(firstMessageSeqNum);
        }

        topicMessageQuery.setStartTime(startTime);
        subscriptionResponse = subscribeWithBackgroundMessageEmission();
    }

    @Then("the network should successfully observe these messages")
    public void verifyTopicMessageSubscription() throws Exception {
        assertNotNull(subscriptionResponse, "subscriptionResponse is null");
        assertFalse(subscriptionResponse.errorEncountered(), "Error encountered");

        subscriptionResponse.validateReceivedMessages();
        mirrorClient.unSubscribeFromTopic(subscriptionResponse.getSubscription());
    }

    @Then("the network should successfully observe {long} messages")
    public void verifyTopicMessageSubscription(long expectedMessageCount) throws Exception {
        assertNotNull(subscriptionResponse, "subscriptionResponse is null");
        assertFalse(subscriptionResponse.errorEncountered(), "Error encountered");

        subscriptionResponse.validateReceivedMessages();
        mirrorClient.unSubscribeFromTopic(subscriptionResponse.getSubscription());
    }

    @Then("the network should confirm valid topic messages were received")
    public void verifyTopicMessages() throws Exception {
        subscriptionResponse.validateReceivedMessages();
    }

    /**
     * Subscribe to topic and observe messages while emitting background messages to encourage service file close in
     * environments with low traffic.
     *
     * @return SubscriptionResponse
     * @throws InterruptedException
     */
    public SubscriptionResponse subscribeWithBackgroundMessageEmission() throws Throwable {
        ScheduledExecutorService scheduler = null;
        if (acceptanceProps.isEmitBackgroundMessages()) {
            log.debug("Emit a background message every second during subscription");
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(
                    () -> {
                        try {
                            topicClient.publishMessageToTopic(
                                    consensusTopicId, "backgroundMessage".getBytes(StandardCharsets.UTF_8), getKeys());
                        } catch (Exception e) {
                            log.error("Error publishing to topic", e);
                        }
                    },
                    0,
                    1,
                    TimeUnit.SECONDS);
        }

        SubscriptionResponse topicSubscriptionResponse;

        try {
            topicSubscriptionResponse = mirrorClient.subscribeToTopicAndRetrieveMessages(
                    sdkClient, topicMessageQuery, messageSubscribeCount, latency);
            assertEquals(
                    messageSubscribeCount,
                    topicSubscriptionResponse.getMirrorHCSResponses().size());
        } finally {
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        }

        return topicSubscriptionResponse;
    }

    private KeyList getKeys() {
        return privateKey == null ? null : KeyList.of(privateKey);
    }

    private long getTokenBalance(AccountId accountId, TokenId tokenId) {
        var tokenRelationships =
                mirrorClient.getTokenRelationships(accountId, tokenId).getTokens();
        assertThat(tokenRelationships).isNotNull().hasSize(1);
        return tokenRelationships.getFirst().getBalance();
    }
}
