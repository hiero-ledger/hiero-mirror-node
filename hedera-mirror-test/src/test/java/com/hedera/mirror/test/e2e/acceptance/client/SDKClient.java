// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.test.e2e.acceptance.client;

import static com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties.HederaNetwork.OTHER;
import static org.awaitility.Awaitility.await;

import com.google.common.base.Stopwatch;
import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TopicDeleteTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.config.SdkProperties;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.NodeProperties;
import jakarta.inject.Named;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Durations;
import org.springframework.util.CollectionUtils;

@CustomLog
@Named
@Value
public class SDKClient implements Cleanable {

    private static final String RESET_IP = "1.0.0.0";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Client client;
    private final ExpandedAccountId defaultOperator;
    private final Map<String, AccountId> validateNetworkMap;
    private final AcceptanceTestProperties acceptanceTestProperties;
    private final SdkProperties sdkProperties;
    private final MirrorNodeClient mirrorNodeClient;
    private final TopicId topicId;

    @Getter
    private final ExpandedAccountId expandedOperatorAccountId;

    public SDKClient(
            AcceptanceTestProperties acceptanceTestProperties,
            MirrorNodeClient mirrorNodeClient,
            SdkProperties sdkProperties,
            StartupProbe startupProbe)
            throws InterruptedException, TimeoutException {
        defaultOperator = new ExpandedAccountId(
                acceptanceTestProperties.getOperatorId(), acceptanceTestProperties.getOperatorKey());
        this.mirrorNodeClient = mirrorNodeClient;
        this.acceptanceTestProperties = acceptanceTestProperties;
        this.sdkProperties = sdkProperties;
        this.client = createClient();
        client.setGrpcDeadline(sdkProperties.getGrpcDeadline())
                .setMaxAttempts(sdkProperties.getMaxAttempts())
                .setMaxNodeReadmitTime(Duration.ofSeconds(60L))
                .setMaxNodesPerTransaction(sdkProperties.getMaxNodesPerTransaction());
        var receipt = startupProbe.validateEnvironment(client);
        this.topicId = receipt != null ? receipt.topicId : null;
        validateClient();
        expandedOperatorAccountId = getOperatorAccount(receipt);
        this.client.setOperator(expandedOperatorAccountId.getAccountId(), expandedOperatorAccountId.getPrivateKey());
        validateNetworkMap = this.client.getNetwork();
    }

    public AccountId getRandomNodeAccountId() {
        int randIndex = RANDOM.nextInt(0, validateNetworkMap.size() - 1);
        return new ArrayList<>(validateNetworkMap.values()).get(randIndex);
    }

    @Override
    public void clean() {
        var createdAccountId = expandedOperatorAccountId.getAccountId();
        var operatorId = defaultOperator.getAccountId();

        if (topicId != null) {
            try {
                var response = new TopicDeleteTransaction()
                        .setTopicId(topicId)
                        .freezeWith(client)
                        .sign(defaultOperator.getPrivateKey())
                        .execute(client);
                log.info("Deleted startup probe topic {} via {}", topicId, response.transactionId);
            } catch (Exception e) {
                log.warn("Unable to delete startup probe topic {}", topicId, e);
            }
        }

        if (!operatorId.equals(createdAccountId)) {
            try {
                var response = new AccountDeleteTransaction()
                        .setAccountId(createdAccountId)
                        .setTransferAccountId(operatorId)
                        .execute(client)
                        .getReceipt(client);
                log.info("Deleted temporary operator account {} via {}", createdAccountId, response.transactionId);
            } catch (Exception e) {
                log.warn("Unable to delete temporary operator account {}", createdAccountId, e);
            }
        }

        try {
            client.close();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    private Client createClient() throws InterruptedException {
        var customNodes = acceptanceTestProperties.getNodes();
        var network = acceptanceTestProperties.getNetwork();

        if (!CollectionUtils.isEmpty(customNodes)) {
            log.debug("Creating SDK client for {} network with nodes: {}", network, customNodes);
            return toClient(getNetworkMap(customNodes));
        }

        if (acceptanceTestProperties.isRetrieveAddressBook()) {
            try {
                log.info("Waiting for a valid address book");
                var addressBook = await("retrieveAddressBook")
                        .atMost(acceptanceTestProperties.getStartupTimeout())
                        .pollDelay(Duration.ofMillis(100))
                        .pollInterval(Durations.FIVE_SECONDS)
                        .until(this::getAddressBook, ab -> !ab.isEmpty());
                return toClient(addressBook);
            } catch (Exception e) {
                log.warn("Error retrieving address book", e);
            }
        }

        if (network == OTHER && CollectionUtils.isEmpty(customNodes)) {
            throw new IllegalArgumentException("nodes must not be empty when network is OTHER");
        }

        return withDefaultOperator(Client.forName(network.toString().toLowerCase()));
    }

    private Map<String, AccountId> getNetworkMap(Set<NodeProperties> nodes) {
        return nodes.stream()
                .collect(Collectors.toMap(NodeProperties::getEndpoint, p -> AccountId.fromString(p.getAccountId())));
    }

    private double getExchangeRate(TransactionReceipt receipt) {
        if (receipt == null || receipt.exchangeRate == null) {
            var currentRate = mirrorNodeClient.getExchangeRates().getCurrentRate();
            int cents = currentRate.getCentEquivalent();
            int hbars = currentRate.getHbarEquivalent();
            return (double) cents / (double) hbars;
        } else {
            return receipt.exchangeRate.exchangeRateInCents;
        }
    }

    private ExpandedAccountId getOperatorAccount(TransactionReceipt receipt) {
        try {
            if (acceptanceTestProperties.isCreateOperatorAccount()) {
                // Use the same operator key in case we need to later manually update/delete any created entities.
                var privateKey = defaultOperator.getPrivateKey();
                var publicKey = privateKey.getPublicKey();
                var alias = privateKey.isECDSA() ? publicKey.toEvmAddress() : null;

                // Convert USD balance property to hbars using exchange rate from probe
                double exchangeRate = getExchangeRate(receipt);
                var exchangeRateUsd =
                        BigDecimal.valueOf(exchangeRate).divide(BigDecimal.valueOf(100), MathContext.DECIMAL128);
                var balance = Hbar.from(acceptanceTestProperties
                        .getOperatorBalance()
                        .divide(exchangeRateUsd, 8, RoundingMode.HALF_EVEN));

                var accountId = new AccountCreateTransaction()
                        .setAlias(alias)
                        .setInitialBalance(balance)
                        .setKey(publicKey)
                        .execute(client)
                        .getReceipt(client)
                        .accountId;
                log.info("Created operator account {} with public key {}", accountId, publicKey);
                return new ExpandedAccountId(accountId, privateKey);
            }
        } catch (Exception e) {
            log.warn("Unable to create a regular operator account. Falling back to existing operator", e);
        }

        return defaultOperator;
    }

    private void validateClient() throws InterruptedException, TimeoutException {
        var network = client.getNetwork();
        Map<String, AccountId> validNodes = new LinkedHashMap<>();
        var stopwatch = Stopwatch.createStarted();

        for (var nodeEntry : network.entrySet()) {
            var endpoint = nodeEntry.getKey();
            var nodeAccountId = nodeEntry.getValue();

            if (validateNode(endpoint, nodeAccountId)) {
                validNodes.putIfAbsent(endpoint, nodeAccountId);
                log.trace("Added node {} at endpoint {} to list of valid nodes", nodeAccountId, endpoint);
            }

            if (validNodes.size() >= acceptanceTestProperties.getMaxNodes()) {
                break;
            }
        }

        log.info("Validated {} of {} endpoints in {}", validNodes.size(), network.size(), stopwatch);
        if (validNodes.size() == 0) {
            throw new IllegalStateException("All provided nodes are unreachable!");
        }

        client.setNetwork(validNodes);
        log.info("Validated client with nodes: {}", validNodes);
    }

    private Client toClient(Map<String, AccountId> network) throws InterruptedException {
        return withDefaultOperator(Client.forNetwork(network))
                .setMirrorNetwork(List.of(acceptanceTestProperties.getMirrorNodeAddress()));
    }

    private boolean validateNode(String endpoint, AccountId nodeAccountId) {
        boolean valid = false;
        var stopwatch = Stopwatch.createStarted();

        try (Client networkClient = toClient(Map.of(endpoint, nodeAccountId))) {
            new AccountBalanceQuery()
                    .setAccountId(nodeAccountId)
                    .setGrpcDeadline(sdkProperties.getGrpcDeadline())
                    .setNodeAccountIds(List.of(nodeAccountId))
                    .setMaxAttempts(3)
                    .setMaxBackoff(Duration.ofSeconds(2))
                    .execute(networkClient, Duration.ofSeconds(10L));
            log.info("Validated node {} in {}", nodeAccountId, stopwatch);
            valid = true;
        } catch (Exception e) {
            log.warn("Unable to validate node {} after {}: {}", nodeAccountId, stopwatch, e.getMessage());
        }

        return valid;
    }

    private Client withDefaultOperator(Client client) {
        var maxTransactionFee = Hbar.fromTinybars(acceptanceTestProperties.getMaxTinyBarTransactionFee());
        return client.setOperator(defaultOperator.getAccountId(), defaultOperator.getPrivateKey())
                .setDefaultMaxTransactionFee(maxTransactionFee);
    }

    private Map<String, AccountId> getAddressBook() {
        Map<String, AccountId> networkMap = new HashMap<>();
        var nodes = mirrorNodeClient.getNetworkNodes();

        for (var node : nodes) {
            var accountId = AccountId.fromString(node.getNodeAccountId());
            for (var serviceEndpoint : node.getServiceEndpoints()) {
                var ip = serviceEndpoint.getIpAddressV4();
                var port = serviceEndpoint.getPort();
                if (port == 50211 && StringUtils.isNotBlank(ip) && !RESET_IP.equals(ip)) {
                    networkMap.putIfAbsent(ip + ":" + port, accountId);
                }
            }
        }

        log.info("Obtained address book with {} nodes and {} endpoints", nodes.size(), networkMap.size());
        return networkMap;
    }
}
