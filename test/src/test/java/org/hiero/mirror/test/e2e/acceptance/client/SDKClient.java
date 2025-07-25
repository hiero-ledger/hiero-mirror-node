// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties.HederaNetwork.OTHER;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;

import com.google.common.base.Stopwatch;
import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TopicDeleteTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionReceiptQuery;
import com.hedera.hashgraph.sdk.proto.AccountID;
import com.hedera.hashgraph.sdk.proto.NodeAddress;
import com.hedera.hashgraph.sdk.proto.NodeAddressBook;
import com.hedera.hashgraph.sdk.proto.ServiceEndpoint;
import jakarta.inject.Named;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import lombok.CustomLog;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.commons.lang3.Strings;
import org.awaitility.Durations;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.hiero.mirror.test.e2e.acceptance.config.SdkProperties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.props.NodeProperties;
import org.hiero.mirror.test.e2e.acceptance.util.TestUtil;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.CollectionUtils;

@CustomLog
@Named
@Value
public class SDKClient implements Cleanable {

    private static final String RESET_IP = "1.0.0.0";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Client client;
    private final Map<String, AccountId> validateNetworkMap;
    private final AcceptanceTestProperties acceptanceTestProperties;
    private final SdkProperties sdkProperties;
    private final MirrorNodeClient mirrorNodeClient;
    private final RetryTemplate retryTemplate;
    private final TopicId topicId;

    @Getter
    private final ExpandedAccountId defaultOperator;

    @Getter
    private final ExpandedAccountId expandedOperatorAccountId;

    public SDKClient(
            AcceptanceTestProperties acceptanceTestProperties,
            MirrorNodeClient mirrorNodeClient,
            RetryTemplate retryTemplate,
            SdkProperties sdkProperties,
            StartupProbe startupProbe)
            throws InterruptedException, TimeoutException {
        try {
            defaultOperator = new ExpandedAccountId(
                    acceptanceTestProperties.getOperatorId(), acceptanceTestProperties.getOperatorKey());
            this.mirrorNodeClient = mirrorNodeClient;
            this.acceptanceTestProperties = acceptanceTestProperties;
            this.retryTemplate = retryTemplate;
            this.sdkProperties = sdkProperties;
            this.client = createClient();
            var receipt = startupProbe.validateEnvironment(client);
            this.topicId = receipt != null ? receipt.topicId : null;
            validateClient();
            expandedOperatorAccountId = getOperatorAccount(receipt);
            this.client.setOperator(
                    expandedOperatorAccountId.getAccountId(), expandedOperatorAccountId.getPrivateKey());
            validateNetworkMap = this.client.getNetwork();
        } catch (Throwable t) {
            clean();
            throw t;
        }
    }

    public AccountId getRandomNodeAccountId() {
        int randIndex = RANDOM.nextInt(0, validateNetworkMap.size() - 1);
        return new ArrayList<>(validateNetworkMap.values()).get(randIndex);
    }

    @Override
    public void clean() {
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

    private Client createClient() {
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
                        .until(this::getAddressBook, ab -> ab.getNodeAddressCount() > 0);
                return toClient(addressBook);
            } catch (Exception e) {
                log.warn("Error retrieving address book", e);
            }
        }

        if (network == OTHER && CollectionUtils.isEmpty(customNodes)) {
            throw new IllegalArgumentException("nodes must not be empty when network is OTHER");
        }

        return configureClient(Client.forName(network.toString().toLowerCase()));
    }

    private NodeAddressBook getNetworkMap(Set<NodeProperties> nodes) {
        var nodeAddresses = nodes.stream().map(NodeProperties::toNodeAddress).toList();
        return NodeAddressBook.newBuilder().addAllNodeAddress(nodeAddresses).build();
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

                var response = new AccountCreateTransaction()
                        .setAlias(alias)
                        .setInitialBalance(balance)
                        .setKeyWithoutAlias(publicKey)
                        .execute(client);

                // Verify all nodes have created the account since state is updated at different wall clocks
                TransactionReceipt queryReceipt = null;
                for (final var nodeAccountId : client.getNetwork().values()) {
                    queryReceipt = retryTemplate.execute(x -> new TransactionReceiptQuery()
                            .setNodeAccountIds(List.of(nodeAccountId))
                            .setTransactionId(response.transactionId)
                            .execute(client));
                }

                var accountId = queryReceipt.accountId;
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
        var invalidNodes = new HashSet<AccountId>();

        for (var nodeEntry : network.entrySet()) {
            var endpoint = nodeEntry.getKey();
            var nodeAccountId = nodeEntry.getValue();

            if (validateNode(endpoint, nodeAccountId)) {
                validNodes.putIfAbsent(endpoint, nodeAccountId);
            } else {
                invalidNodes.add(nodeAccountId);
            }

            if (validNodes.size() >= acceptanceTestProperties.getMaxNodes()) {
                break;
            }
        }

        // Workaround SDK #2317 not propagating the address book when calling setNetwork(), causing TLS to fail
        var iterator = validNodes.entrySet().iterator();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (invalidNodes.contains(next.getValue())) {
                iterator.remove();
            }
        }

        log.info("Validated {} of {} endpoints in {}", validNodes.size(), network.size(), stopwatch);
        if (validNodes.size() == 0) {
            throw new IllegalStateException("All provided nodes are unreachable!");
        }

        client.setNetwork(validNodes);
        log.info("Validated client with nodes: {}", validNodes);
    }

    @SneakyThrows
    private Client toClient(NodeAddressBook addressBook) {
        var client = Client.forNetwork(Map.of());
        client.setNetworkFromAddressBook(
                com.hedera.hashgraph.sdk.NodeAddressBook.fromBytes(addressBook.toByteString()));
        return configureClient(client).setMirrorNetwork(List.of(acceptanceTestProperties.getMirrorNodeAddress()));
    }

    private boolean validateNode(String endpoint, AccountId nodeAccountId) {
        var stopwatch = Stopwatch.createStarted();

        try {
            // client.setNetwork(Map.of(endpoint, nodeAccountId)); Enable after SDK #2317 is fixed
            new TopicMessageSubmitTransaction()
                    .setMessage("Mirror Acceptance node " + nodeAccountId + " validation")
                    .setTopicId(topicId)
                    .setMaxAttempts(3)
                    .setMaxBackoff(Duration.ofSeconds(2))
                    .setNodeAccountIds(List.of(nodeAccountId))
                    .execute(client, Duration.ofSeconds(10L))
                    .getReceipt(client, Duration.ofSeconds(10L));
            log.info("Validated node {} {} in {}", nodeAccountId, endpoint, stopwatch);
            return true;
        } catch (Exception e) {
            log.warn("Unable to validate node {} {} after {}: {}", nodeAccountId, endpoint, stopwatch, e.getMessage());
        }

        return false;
    }

    private Client configureClient(Client client) {
        var maxTransactionFee = Hbar.fromTinybars(acceptanceTestProperties.getMaxTinyBarTransactionFee());
        return client.setDefaultMaxTransactionFee(maxTransactionFee)
                .setGrpcDeadline(sdkProperties.getGrpcDeadline())
                .setMaxAttempts(sdkProperties.getMaxAttempts())
                .setMaxNodeReadmitTime(Duration.ofSeconds(60L))
                .setMaxNodesPerTransaction(sdkProperties.getMaxNodesPerTransaction())
                .setOperator(defaultOperator.getAccountId(), defaultOperator.getPrivateKey());
    }

    private NodeAddressBook getAddressBook() {
        var nodeAddressBook = NodeAddressBook.newBuilder();
        var nodes = mirrorNodeClient.getNetworkNodes();
        int endpoints = 0;

        for (var node : nodes) {
            var accountId = AccountId.fromString(node.getNodeAccountId());
            var nodeAddress = NodeAddress.newBuilder()
                    .setDescription(node.getDescription())
                    .setNodeAccountId(AccountID.newBuilder()
                            .setShardNum(accountId.shard)
                            .setRealmNum(accountId.realm)
                            .setAccountNum(accountId.num))
                    .setNodeCertHash(ByteString.copyFromUtf8(Strings.CS.remove(node.getNodeCertHash(), HEX_PREFIX)))
                    .setRSAPubKey(node.getPublicKey())
                    .setNodeId(node.getNodeId());

            for (var serviceEndpoint : node.getServiceEndpoints()) {
                var ip = serviceEndpoint.getIpAddressV4();
                if (!RESET_IP.equals(ip)) {
                    try {
                        nodeAddress.addServiceEndpoint(ServiceEndpoint.newBuilder()
                                .setDomainName(serviceEndpoint.getDomainName())
                                .setIpAddressV4(TestUtil.toIpAddressV4(serviceEndpoint.getIpAddressV4()))
                                .setPort(serviceEndpoint.getPort()));
                        ++endpoints;
                    } catch (Exception e) {
                        log.warn("Unable to convert service endpoint {}: {}", serviceEndpoint, e.getMessage());
                    }
                }
            }

            nodeAddressBook.addNodeAddress(nodeAddress);
        }

        log.info("Obtained address book with {} nodes and {} endpoints", nodes.size(), endpoints);
        return nodeAddressBook.build();
    }
}
