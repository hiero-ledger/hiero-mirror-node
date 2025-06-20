// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.test.e2e.acceptance.config.RestProperties.URL_PREFIX;

import com.google.common.base.Stopwatch;
import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.LedgerId;
import com.hedera.hashgraph.sdk.MirrorNodeContractEstimateGasQuery;
import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import jakarta.inject.Named;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.CustomLog;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.hiero.mirror.rest.model.AccountBalanceTransactions;
import org.hiero.mirror.rest.model.AccountInfo;
import org.hiero.mirror.rest.model.BlocksResponse;
import org.hiero.mirror.rest.model.ContractActionsResponse;
import org.hiero.mirror.rest.model.ContractCallRequest;
import org.hiero.mirror.rest.model.ContractCallResponse;
import org.hiero.mirror.rest.model.ContractResponse;
import org.hiero.mirror.rest.model.ContractResult;
import org.hiero.mirror.rest.model.ContractResultsResponse;
import org.hiero.mirror.rest.model.CryptoAllowancesResponse;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.NetworkNodesResponse;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.hiero.mirror.rest.model.Nft;
import org.hiero.mirror.rest.model.NftAllowancesResponse;
import org.hiero.mirror.rest.model.NftTransactionHistory;
import org.hiero.mirror.rest.model.Nfts;
import org.hiero.mirror.rest.model.Schedule;
import org.hiero.mirror.rest.model.TokenAirdropsResponse;
import org.hiero.mirror.rest.model.TokenAllowancesResponse;
import org.hiero.mirror.rest.model.TokenBalancesResponse;
import org.hiero.mirror.rest.model.TokenInfo;
import org.hiero.mirror.rest.model.TokenRelationshipResponse;
import org.hiero.mirror.rest.model.TokensResponse;
import org.hiero.mirror.rest.model.Topic;
import org.hiero.mirror.rest.model.TopicMessage;
import org.hiero.mirror.rest.model.TopicMessagesResponse;
import org.hiero.mirror.rest.model.TransactionByIdResponse;
import org.hiero.mirror.rest.model.TransactionsResponse;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.hiero.mirror.test.e2e.acceptance.config.RestJavaProperties;
import org.hiero.mirror.test.e2e.acceptance.config.Web3Properties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.props.Order;
import org.hiero.mirror.test.e2e.acceptance.util.TestUtil;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

@CustomLog
@Named
public class MirrorNodeClient {
    private static final int DEFAULT_PERCENTAGE_OF_ACTUAL_GAS_USED = 30;
    private static final int DEFAULT_PERCENTAGE_OF_ACTUAL_GAS_USED_NESTED_CALLS = 100;
    private static final long GAS_PRICE = 1_000_000L;

    private final AcceptanceTestProperties acceptanceTestProperties;
    private final RestClient restClient;
    private final RestClient restJavaClient;
    private final RetryTemplate retryTemplate;
    private final RestClient web3Client;
    private final Web3Properties web3Properties;
    private final Client queryClient;
    private final ExpandedAccountId defaultOperator;

    public MirrorNodeClient(
            AcceptanceTestProperties acceptanceTestProperties,
            RestClient.Builder restClientBuilder,
            RestJavaProperties restJavaProperties,
            Web3Properties web3Properties)
            throws InterruptedException, TimeoutException, URISyntaxException {
        defaultOperator = new ExpandedAccountId(
                acceptanceTestProperties.getOperatorId(), acceptanceTestProperties.getOperatorKey());
        this.acceptanceTestProperties = acceptanceTestProperties;
        this.restClient = restClientBuilder.build();
        this.restJavaClient = StringUtils.isBlank(restJavaProperties.getBaseUrl())
                ? restClient
                : restClientBuilder.baseUrl(restJavaProperties.getBaseUrl()).build();
        this.web3Client = StringUtils.isBlank(web3Properties.getBaseUrl())
                ? restClient
                : restClientBuilder.baseUrl(web3Properties.getBaseUrl()).build();
        var properties = acceptanceTestProperties.getRestProperties();
        this.retryTemplate = RetryTemplate.builder()
                .customPolicy(new MaxAttemptsRetryPolicy(properties.getMaxAttempts()) {
                    @Override
                    public boolean canRetry(RetryContext context) {
                        return super.canRetry(context) && properties.shouldRetry(context.getLastThrowable());
                    }
                })
                .exponentialBackoff(properties.getMinBackoff(), 2.0, properties.getMaxBackoff())
                .build();
        this.web3Properties = web3Properties;
        this.queryClient = createClient();

        var virtualThreadFactory = Thread.ofVirtual().name("awaitility", 1).factory();
        var executorService = Executors.newThreadPerTaskExecutor(virtualThreadFactory);
        Awaitility.pollExecutorService(executorService);
    }

    public SubscriptionResponse subscribeToTopic(SDKClient sdkClient, TopicMessageQuery topicMessageQuery)
            throws Throwable {
        log.debug("Subscribing to topic.");
        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        SubscriptionHandle subscription = topicMessageQuery
                .setErrorHandler(subscriptionResponse::handleThrowable)
                .subscribe(sdkClient.getClient(), subscriptionResponse::handleConsensusTopicResponse);

        subscriptionResponse.setSubscription(subscription);

        // allow time for connection to be made and error to be caught
        await("responseEncountered")
                .atMost(Durations.ONE_MINUTE)
                .pollInterval(Durations.ONE_SECOND)
                .pollDelay(Durations.ONE_HUNDRED_MILLISECONDS)
                .until(subscriptionResponse::hasResponse);

        if (subscriptionResponse.errorEncountered()) {
            throw subscriptionResponse.getResponseError();
        }

        return subscriptionResponse;
    }

    public SubscriptionResponse subscribeToTopicAndRetrieveMessages(
            SDKClient sdkClient, TopicMessageQuery topicMessageQuery, int numMessages, long latency) throws Throwable {
        latency = latency <= 0 ? acceptanceTestProperties.getMessageTimeout().toSeconds() : latency;
        log.debug("Subscribing to topic, expecting {} within {} seconds.", numMessages, latency);

        CountDownLatch messageLatch = new CountDownLatch(numMessages);
        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        Stopwatch stopwatch = Stopwatch.createStarted();

        SubscriptionHandle subscription = topicMessageQuery
                .setErrorHandler(subscriptionResponse::handleThrowable)
                .subscribe(sdkClient.getClient(), resp -> {
                    // add expected messages only to messages list
                    if (subscriptionResponse.getMirrorHCSResponses().size() < numMessages) {
                        subscriptionResponse.handleConsensusTopicResponse(resp);
                    }
                    messageLatch.countDown();
                });

        subscriptionResponse.setSubscription(subscription);

        if (!messageLatch.await(latency, TimeUnit.SECONDS)) {
            stopwatch.stop();
            log.error(
                    "{} messages were expected within {} s. {} not yet received after {}",
                    numMessages,
                    latency,
                    messageLatch.getCount(),
                    stopwatch);
        } else {
            stopwatch.stop();
            log.info(
                    "Success, received {} out of {} messages received in {}.",
                    numMessages - messageLatch.getCount(),
                    numMessages,
                    stopwatch);
        }

        subscriptionResponse.setElapsedTime(stopwatch);

        if (subscriptionResponse.errorEncountered()) {
            throw subscriptionResponse.getResponseError();
        }

        return subscriptionResponse;
    }

    public CryptoAllowancesResponse getAccountCryptoAllowance(String accountId) {
        log.debug("Verify account '{}''s crypto allowance is returned by Mirror Node", accountId);
        return callRestEndpoint("/accounts/{accountId}/allowances/crypto", CryptoAllowancesResponse.class, accountId);
    }

    public CryptoAllowancesResponse getAccountCryptoAllowanceBySpender(String accountId, String spenderId) {
        log.debug("Verify account '{}''s crypto allowance for {} is returned by Mirror Node", accountId, spenderId);
        return callRestEndpoint(
                "/accounts/{accountId}/allowances/crypto?spender.id={spenderId}",
                CryptoAllowancesResponse.class,
                accountId,
                spenderId);
    }

    public NftAllowancesResponse getAccountNftAllowanceBySpender(String accountId, String tokenId, String ownerId) {
        log.debug(
                "Verify spender account '{}''s nft allowance for owner {} and token {} is returned by Mirror Node",
                accountId,
                ownerId,
                tokenId);
        return callRestJavaEndpoint(
                "/accounts/{accountId}/allowances/nfts?token.id={tokenId}&account.id={ownerId}&owner=false",
                NftAllowancesResponse.class,
                accountId,
                tokenId,
                ownerId);
    }

    public NftAllowancesResponse getAccountNftAllowanceByOwner(String accountId, String tokenId, String spenderId) {
        log.debug(
                "Verify owner account '{}''s nft allowance for spender {} and token {} is returned by Mirror Node",
                accountId,
                spenderId,
                tokenId);
        return callRestJavaEndpoint(
                "/accounts/{accountId}/allowances/nfts?token.id={tokenId}&account.id={spenderId}&owner=true",
                NftAllowancesResponse.class,
                accountId,
                tokenId,
                spenderId);
    }

    public Nfts getAccountsNftInfo(@NonNull AccountId accountId) {
        log.debug("Retrieving account nft info for '{}' returned by Mirror Node", accountId);
        return callRestEndpoint("/accounts/{accountId}/nfts", Nfts.class, accountId.toString());
    }

    public TokenAllowancesResponse getAccountTokenAllowanceBySpender(
            String accountId, String tokenId, String spenderId) {
        log.debug(
                "Verify account '{}''s token allowance for token {} and spender {} is returned by Mirror Node",
                accountId,
                tokenId,
                spenderId);
        return callRestEndpoint(
                "/accounts/{accountId}/allowances/tokens?token.id={tokenId}&spender.id={spenderId}",
                TokenAllowancesResponse.class,
                accountId,
                tokenId,
                spenderId);
    }

    public ContractResponse getContractInfo(String contractId) {
        log.debug("Verify contract '{}' is returned by Mirror Node", contractId);
        return callRestEndpoint("/contracts/{contractId}", ContractResponse.class, contractId);
    }

    public ContractResponse getContractInfoWithNotFound(String contractId) {
        log.debug("Verify contract '{}' is not found", contractId);
        return callRestEndpointNoRetry("/contracts/{contractId}", ContractResponse.class, contractId);
    }

    public ContractResultsResponse getContractResultsById(String contractId) {
        log.debug("Verify contract results '{}' is returned by Mirror Node", contractId);
        return callRestEndpoint("/contracts/{contractId}/results", ContractResultsResponse.class, contractId);
    }

    public ContractResult getContractResultByTransactionId(String transactionId) {
        log.debug("Verify contract result '{}' is returned by Mirror Node", transactionId);
        return callRestEndpoint("/contracts/results/{transactionId}", ContractResult.class, transactionId);
    }

    public ContractActionsResponse getContractResultActionsByTransactionId(String transactionId) {
        log.debug("Verify contract result '{}' is returned by Mirror Node", transactionId);
        return callRestEndpoint("/contracts/results/{id}/actions", ContractActionsResponse.class, transactionId);
    }

    public NetworkExchangeRateSetResponse getExchangeRates() {
        log.debug("Get exchange rates by Mirror Node");
        return callRestEndpoint("/network/exchangerate", NetworkExchangeRateSetResponse.class);
    }

    public ContractCallResponse contractsCall(ContractCallRequest request) {
        Map<String, String> headers =
                Collections.singletonMap("Is-Modularized", String.valueOf(web3Properties.isModularizedServices()));
        return callPostRestEndpoint("/contracts/call", ContractCallResponse.class, request, headers);
    }

    public BlocksResponse getBlocks(Order order, long limit) {
        log.debug("Get blocks data by Mirror Node");
        return callRestEndpoint("/blocks?order={order}&limit={limit}", BlocksResponse.class, order, limit);
    }

    public List<NetworkNode> getNetworkNodes() {
        List<NetworkNode> nodes = new ArrayList<>();
        String next = "/network/nodes?limit=25";

        do {
            var response = callRestEndpoint(next, NetworkNodesResponse.class);
            nodes.addAll(response.getNodes());
            next = response.getLinks() != null ? response.getLinks().getNext() : null;
        } while (next != null);

        return nodes;
    }

    public NetworkStakeResponse getNetworkStake() {
        String stakeEndpoint = "/network/stake";
        return callRestEndpoint(stakeEndpoint, NetworkStakeResponse.class);
    }

    public Nft getNftInfo(String tokenId, long serialNumber) {
        log.debug("Verify serial number '{}' for token '{}' is returned by Mirror Node", serialNumber, tokenId);
        return callRestEndpoint("/tokens/{tokenId}/nfts/{serialNumber}", Nft.class, tokenId, serialNumber);
    }

    public NftTransactionHistory getNftTransactions(TokenId tokenId, Long serialNumber) {
        log.debug(
                "Get list of transactions for token '{}' and serial number '{}' from Mirror Node",
                tokenId,
                serialNumber);
        return callRestEndpoint(
                "/tokens/{tokenId}/nfts/{serialNumber}/transactions",
                NftTransactionHistory.class,
                tokenId,
                serialNumber);
    }

    public Schedule getScheduleInfo(String scheduleId) {
        log.debug("Verify schedule '{}' is returned by Mirror Node", scheduleId);
        return callRestEndpoint("/schedules/{scheduleId}", Schedule.class, scheduleId);
    }

    public TokenBalancesResponse getTokenBalances(String tokenId) {
        log.debug("Verify token balances '{}' is returned by Mirror Node", tokenId);
        return callRestEndpoint("/tokens/{tokenId}/balances", TokenBalancesResponse.class, tokenId);
    }

    public TokenInfo getTokenInfo(String tokenId) {
        log.debug("Verify token '{}' is returned by Mirror Node", tokenId);
        return callRestEndpoint("/tokens/{tokenId}", TokenInfo.class, tokenId);
    }

    public TokensResponse getTokens(String tokenId) {
        log.debug("Verify token with query parameter '{}' is returned by Mirror Node", tokenId);
        return callRestEndpoint("/tokens/?token.id={tokenId}", TokensResponse.class, tokenId);
    }

    public Topic getTopic(String topicId) {
        return callRestJavaEndpoint("/topics/{topicId}", Topic.class, topicId);
    }

    public TopicMessagesResponse getTopicMessage(String topicId) {
        return callRestEndpoint("/topics/{topicId}/messages", TopicMessagesResponse.class, topicId);
    }

    public TopicMessage getTopicMessageBySequenceNumber(String topicId, String sequenceNumber) {
        return callRestEndpoint(
                "/topics/{topicId}/messages/{sequenceNumber}", TopicMessage.class, topicId, sequenceNumber);
    }

    public TransactionsResponse getTransactionInfoByTimestamp(String timestamp) {
        log.debug("Verify transaction with consensus timestamp '{}' is returned by Mirror Node", timestamp);
        return callRestEndpoint("/transactions?timestamp={timestamp}", TransactionsResponse.class, timestamp);
    }

    public TransactionByIdResponse getTransactions(String transactionId) {
        log.debug("Verify transaction '{}' is returned by Mirror Node", transactionId);
        return callRestEndpoint("/transactions/{transactionId}", TransactionByIdResponse.class, transactionId);
    }

    public TokenRelationshipResponse getTokenRelationships(AccountId accountId, TokenId tokenId) {
        log.debug(
                "Verify tokenRelationship  for account '{}' and token '{}' is returned by Mirror Node",
                accountId,
                tokenId);
        return callRestEndpoint(
                "/accounts/{accountId}/tokens?token.id={tokenId}", TokenRelationshipResponse.class, accountId, tokenId);
    }

    public AccountBalanceTransactions getAccountDetailsUsingAlias(@NonNull AccountId accountId) {
        log.debug("Retrieving account details for accountId '{}'", accountId);
        return callRestEndpoint(
                "/accounts/{accountId}",
                AccountBalanceTransactions.class,
                TestUtil.getAliasFromPublicKey(accountId.aliasKey));
    }

    public AccountInfo getAccountDetailsUsingEvmAddress(@NonNull AccountId accountId) {
        log.debug("Retrieving account details for accountId '{}'", accountId);
        return callRestEndpoint("/accounts/{accountId}", AccountInfo.class, accountId.evmAddress);
    }

    public AccountInfo getAccountDetailsByAccountId(@NonNull AccountId accountId) {
        log.debug("Retrieving account details for accountId '{}'", accountId);
        return callRestEndpoint("/accounts/{accountId}", AccountInfo.class, accountId.toString());
    }

    public void unSubscribeFromTopic(SubscriptionHandle subscription) {
        subscription.unsubscribe();
        log.info("Unsubscribed from {}", subscription);
    }

    public TokenAirdropsResponse getPendingAirdrops(@NonNull AccountId accountId) {
        log.debug("Retrieving pending airdrops for account '{}' returned by Mirror Node", accountId);
        return callRestJavaEndpoint(
                "/accounts/{accountId}/airdrops/pending", TokenAirdropsResponse.class, accountId.toString());
    }

    public TokenAirdropsResponse getOutstandingAirdrops(@NonNull AccountId accountId) {
        log.debug("Retrieving outstanding airdrops for account '{}' returned by Mirror Node", accountId);
        return callRestJavaEndpoint(
                "/accounts/{accountId}/airdrops/outstanding", TokenAirdropsResponse.class, accountId.toString());
    }

    private <T> T callRestEndpoint(String uri, Class<T> classType, Object... uriVariables) {
        String normalizedUri = normalizeUri(uri);
        return retryTemplate.execute(x ->
                restClient.get().uri(normalizedUri, uriVariables).retrieve().body(classType));
    }

    private <T> T callRestJavaEndpoint(String uri, Class<T> classType, Object... uriVariables) {
        String normalizedUri = normalizeUri(uri);
        return retryTemplate.execute(x ->
                restJavaClient.get().uri(normalizedUri, uriVariables).retrieve().body(classType));
    }

    private <T> T callRestEndpointNoRetry(String uri, Class<T> classType, Object... uriVariables) {
        return restClient.get().uri(normalizeUri(uri), uriVariables).retrieve().body(classType);
    }

    private <T, R> T callPostRestEndpoint(String uri, Class<T> classType, R request, Map<String, String> headers) {
        return retryTemplate.execute(x -> {
            final var requestSpec = web3Client.post().uri(uri);
            headers.forEach(requestSpec::header);
            return requestSpec.body(request).retrieve().body(classType);
        });
    }

    public long estimateGasQueryTopLevelCall(
            final ContractId contractId,
            final String functionName,
            final ContractFunctionParameters params,
            final AccountId sender,
            final int actualGas,
            final Optional<Long> value)
            throws ExecutionException, InterruptedException {
        return estimateGasQuery(
                contractId, functionName, params, sender, actualGas, value, DEFAULT_PERCENTAGE_OF_ACTUAL_GAS_USED);
    }

    private long estimateGasQuery(
            final ContractId contractId,
            final String functionName,
            final ContractFunctionParameters params,
            final AccountId sender,
            final int actualGas,
            final Optional<Long> value,
            final int percentage)
            throws ExecutionException, InterruptedException {

        final long calculatedContractCallGas = calculateGasLimit(actualGas, percentage);

        var gasEstimateQuery = buildEstimateGasQueryWithParams(contractId, functionName, params);
        gasEstimateQuery.setGasLimit(calculatedContractCallGas);
        if (sender != null) {
            gasEstimateQuery.setSender(sender);
        }
        value.ifPresent(gasEstimateQuery::setValue);

        return gasEstimateQuery.execute(queryClient);
    }

    public long estimateGasQueryWithoutParams(
            final ContractId contractId, final String functionName, final AccountId sender, final int actualGas)
            throws ExecutionException, InterruptedException {

        final long calculatedContractCallGas = calculateGasLimit(actualGas, DEFAULT_PERCENTAGE_OF_ACTUAL_GAS_USED);

        var gasEstimateQuery = new MirrorNodeContractEstimateGasQuery()
                .setContractId(contractId)
                .setFunction(functionName)
                .setGasPrice(GAS_PRICE)
                .setGasLimit(calculatedContractCallGas);
        if (sender != null) {
            gasEstimateQuery.setSender(sender);
        }

        return gasEstimateQuery.execute(queryClient);
    }

    public long estimateGasQueryRawData(
            final ContractId contractId, final ByteString params, final AccountId sender, final int actualGas)
            throws ExecutionException, InterruptedException {

        final long calculatedContractCallGas = calculateGasLimit(actualGas, DEFAULT_PERCENTAGE_OF_ACTUAL_GAS_USED);

        var gasEstimateQuery = new MirrorNodeContractEstimateGasQuery()
                .setContractId(contractId)
                .setFunctionParameters(params)
                .setGasPrice(GAS_PRICE)
                .setGasLimit(calculatedContractCallGas);
        if (sender != null) {
            gasEstimateQuery.setSender(sender);
        }

        return gasEstimateQuery.execute(queryClient);
    }

    public long estimateGasQueryNestedCall(
            final ContractId contractId,
            final String functionName,
            final ContractFunctionParameters params,
            final AccountId sender,
            final int actualGas)
            throws ExecutionException, InterruptedException {

        return estimateGasQuery(
                contractId,
                functionName,
                params,
                sender,
                actualGas,
                Optional.empty(),
                DEFAULT_PERCENTAGE_OF_ACTUAL_GAS_USED_NESTED_CALLS);
    }

    private MirrorNodeContractEstimateGasQuery buildEstimateGasQueryWithParams(
            ContractId contractId, String functionName, ContractFunctionParameters params) {
        return new MirrorNodeContractEstimateGasQuery()
                .setContractId(contractId)
                .setFunction(functionName, params)
                .setGasPrice(GAS_PRICE);
    }

    private long calculateGasLimit(int actualGas, int percentage) {
        return Math.round(actualGas * (1 + (percentage / 100.0)));
    }

    private String normalizeUri(String uri) {
        if (uri == null || !uri.startsWith(URL_PREFIX)) {
            return uri;
        }

        return uri.substring(URL_PREFIX.length());
    }

    private Client createClient() throws InterruptedException, URISyntaxException {
        var endpoint = StringUtils.isNotBlank(web3Properties.getEndpoint())
                ? web3Properties.getEndpoint()
                : acceptanceTestProperties.getRestProperties().getEndpoint();
        if (acceptanceTestProperties.getNetwork().name().equals("OTHER")) {
            return Client.forNetwork(Map.of()).setMirrorNetwork(List.of(endpoint));
        }
        return Client.forNetwork(Map.of())
                .setMirrorNetwork(List.of(endpoint))
                .setLedgerId(LedgerId.fromString(
                        acceptanceTestProperties.getNetwork().name().toLowerCase()));
    }
}
