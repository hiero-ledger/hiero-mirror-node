// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import static com.hedera.hashgraph.sdk.Status.SUCCESS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.hashgraph.sdk.proto.NodeAddressBook;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.monitor.NodeProperties;
import org.hiero.mirror.monitor.subscribe.rest.RestApiClient;
import org.hiero.mirror.rest.model.NetworkNode;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@CustomLog
@Named
@RequiredArgsConstructor
public class NodeSupplier {

    private final MonitorProperties monitorProperties;
    private final RestApiClient restApiClient;

    private final AtomicLong counter = new AtomicLong(0L);
    private final CopyOnWriteArrayList<NodeProperties> nodes = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        var validationProperties = monitorProperties.getNodeValidation();
        int parallelism = validationProperties.getMaxThreads();
        long retryBackoff = validationProperties.getRetryBackoff().toMillis();

        var scheduler = Schedulers.newParallel("validator", parallelism + 1);
        Flux.interval(Duration.ZERO, validationProperties.getFrequency(), scheduler)
                .flatMap(i -> refresh()
                        .take(monitorProperties.getNodeValidation().getMaxNodes())
                        .parallel(parallelism)
                        .runOn(scheduler)
                        .map(this::validateNode)
                        .sequential()
                        .collectList()
                        .doOnNext(valid -> log.info(
                                "{} of {} nodes are functional",
                                valid.stream().filter(v -> v).count(),
                                valid.size()))
                        .repeatWhen(repeat -> repeat.filter(l -> nodes.isEmpty())
                                .doOnNext(numEmitted -> log.info("Retrying in {} ms", retryBackoff))
                                .flatMap(numEmitted -> Mono.delay(Duration.ofMillis(retryBackoff), scheduler))))
                .doOnSubscribe(s -> log.info("Starting node validation"))
                .doOnError(t -> log.error("Exception validating nodes: ", t))
                .onErrorResume(t -> Mono.empty())
                .subscribe();
    }

    public NodeProperties get() {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No valid nodes available");
        }

        long nodeIndex = counter.getAndIncrement() % nodes.size();
        return nodes.get((int) nodeIndex);
    }

    public synchronized Flux<NodeProperties> refresh() {
        boolean empty = nodes.isEmpty();
        Retry retrySpec = Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1L))
                .maxBackoff(Duration.ofSeconds(8L))
                .scheduler(Schedulers.newSingle("nodes"))
                .doBeforeRetry(r -> log.warn(
                        "Retry attempt #{} after failure: {}",
                        r.totalRetries() + 1,
                        r.failure().getMessage()));

        var predicate = monitorProperties.getNodeValidation().getTls().getPredicate();
        return Flux.fromIterable(monitorProperties.getNodes())
                .doOnSubscribe(s -> log.info("Refreshing node list"))
                .switchIfEmpty(Flux.defer(this::getAddressBook))
                .switchIfEmpty(Flux.fromIterable(monitorProperties.getNetwork().getNodes()))
                .filter(n -> predicate.test(n.getPort()))
                .retryWhen(retrySpec)
                .switchIfEmpty(Flux.error(new IllegalArgumentException("Nodes must not be empty")))
                .doOnNext(n -> {
                    if (empty) {
                        nodes.addIfAbsent(n);
                    }
                }); // Populate on startup before validation
    }

    private Flux<NodeProperties> getAddressBook() {
        if (!monitorProperties.getNodeValidation().isRetrieveAddressBook()) {
            return Flux.empty();
        }

        var count = new AtomicInteger(0);

        return Flux.defer(restApiClient::getNodes)
                .filter(n -> !CollectionUtils.isEmpty(n.getServiceEndpoints()))
                .flatMapIterable(this::toNodeProperties)
                .doOnNext(n -> count.incrementAndGet())
                .doOnComplete(() -> log.info("Retrieved {} nodes from address book", count));
    }

    private List<NodeProperties> toNodeProperties(NetworkNode networkNode) {
        var nodeValidation = monitorProperties.getNodeValidation();
        var tlsPredicate = nodeValidation.getTls().getPredicate();
        return networkNode.getServiceEndpoints().stream()
                .filter(s -> tlsPredicate.test(s.getPort()))
                .limit(nodeValidation.getMaxEndpointsPerNode())
                .map(serviceEndpoint -> {
                    var host = StringUtils.isNotBlank(serviceEndpoint.getDomainName())
                            ? serviceEndpoint.getDomainName()
                            : serviceEndpoint.getIpAddressV4();
                    var nodeProperties = new NodeProperties();
                    nodeProperties.setAccountId(networkNode.getNodeAccountId());
                    nodeProperties.setCertHash(Strings.CS.remove(networkNode.getNodeCertHash(), "0x"));
                    nodeProperties.setHost(host);
                    nodeProperties.setNodeId(networkNode.getNodeId());
                    nodeProperties.setPort(serviceEndpoint.getPort());
                    return nodeProperties;
                })
                .toList();
    }

    @SneakyThrows
    private Client toClient(NodeProperties node) {
        var operatorId = AccountId.fromString(monitorProperties.getOperator().getAccountId());
        var operatorPrivateKey =
                PrivateKey.fromString(monitorProperties.getOperator().getPrivateKey());
        var validationProperties = monitorProperties.getNodeValidation();

        var network = Map.of(node.getEndpoint(), AccountId.fromString(node.getAccountId()));
        var nodeAddress = node.toNodeAddress();
        var nodeAddressBook =
                NodeAddressBook.newBuilder().addNodeAddress(nodeAddress).build().toByteString();

        var client = Client.forNetwork(Map.of());
        client.setNetworkFromAddressBook(com.hedera.hashgraph.sdk.NodeAddressBook.fromBytes(nodeAddressBook));
        client.setNetwork(network);
        client.setMaxAttempts(validationProperties.getMaxAttempts());
        client.setMaxBackoff(validationProperties.getMaxBackoff());
        client.setMinBackoff(validationProperties.getMinBackoff());
        client.setOperator(operatorId, operatorPrivateKey);
        client.setRequestTimeout(validationProperties.getRequestTimeout());
        return client;
    }

    @VisibleForTesting
    boolean validateNode(NodeProperties node) {
        if (!monitorProperties.getNodeValidation().isEnabled()) {
            nodes.addIfAbsent(node);
            log.info("Adding node {} without validation", node.getAccountId());
            return true;
        }

        log.info("Validating node {}", node);
        Hbar hbar = Hbar.fromTinybars(1L);
        AccountId nodeAccountId = AccountId.fromString(node.getAccountId());

        try (Client client = toClient(node)) {
            Status receiptStatus = new TransferTransaction()
                    .addHbarTransfer(nodeAccountId, hbar)
                    .addHbarTransfer(client.getOperatorAccountId(), hbar.negated())
                    .setNodeAccountIds(node.getAccountIds())
                    .execute(client)
                    .getReceipt(client)
                    .status;

            if (receiptStatus == SUCCESS) {
                log.info("Validated node {} successfully", nodeAccountId);
                nodes.addIfAbsent(node);
                return true;
            }

            log.warn("Unable to validate node {}: invalid status code {}", node, receiptStatus);
        } catch (TimeoutException e) {
            log.warn("Unable to validate node {}: Timed out", node);
        } catch (Exception e) {
            log.warn("Unable to validate node {}: ", node, e);
        }

        nodes.remove(node);
        return false;
    }
}
