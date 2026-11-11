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
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.hiero.mirror.monitor.HederaNetwork;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.monitor.NodeProperties;
import org.hiero.mirror.monitor.NodeValidationProperties.TlsMode;
import org.hiero.mirror.monitor.subscribe.rest.RestApiClient;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@CustomLog
@Named
@RequiredArgsConstructor
public class NodeSupplier {

    private final MonitorProperties monitorProperties;
    private final RestApiClient restApiClient;

    private final AtomicLong counter = new AtomicLong(0L);
    // Immutable snapshot of the validated nodes, replaced on every change so publishers can detect updates
    private final AtomicReference<List<NodeProperties>> nodes = new AtomicReference<>(List.of());
    private final Scheduler refreshScheduler = Schedulers.newSingle("nodes");
    private Scheduler validationScheduler;

    @PreDestroy
    public void destroy() {
        refreshScheduler.dispose();
        if (validationScheduler != null) {
            validationScheduler.dispose();
        }
    }

    @PostConstruct
    public void init() {
        var validationProperties = monitorProperties.getNodeValidation();
        int parallelism = validationProperties.getMaxThreads();
        long retryBackoff = validationProperties.getRetryBackoff().toMillis();
        validationScheduler = Schedulers.newParallel("validator", parallelism + 1);

        Flux.interval(Duration.ZERO, validationProperties.getFrequency(), validationScheduler)
                .onBackpressureDrop(
                        tick -> log.info("Skipping refresh {} since previous refresh is still running", tick))
                .flatMap(
                        i -> refresh()
                                .take(monitorProperties.getNodeValidation().getMaxNodes())
                                .parallel(parallelism)
                                .runOn(validationScheduler)
                                .map(this::validateNode)
                                .sequential()
                                .collectList()
                                .doOnNext(valid -> log.info(
                                        "{} of {} nodes are functional",
                                        valid.stream().filter(v -> v).count(),
                                        valid.size()))
                                .repeatWhen(repeat -> repeat.takeWhile(
                                                _ -> getNodes().isEmpty())
                                        .doOnNext(_ -> log.info("Retrying in {} ms", retryBackoff))
                                        .flatMap(_ -> Mono.delay(Duration.ofMillis(retryBackoff), validationScheduler)))
                                .onErrorResume(t -> {
                                    log.warn("Exception validating nodes: {}", t.getMessage());
                                    return Mono.empty();
                                }),
                        1)
                .doOnSubscribe(s -> log.info("Starting node validation"))
                .subscribe();
    }

    public NodeProperties get() {
        final var current = getNodes();
        if (current.isEmpty()) {
            throw new IllegalArgumentException("No valid nodes available");
        }

        final long nodeIndex = counter.getAndIncrement() % current.size();
        return current.get((int) nodeIndex);
    }

    /**
     * Returns the current immutable snapshot of validated nodes. The same instance is returned until the nodes change.
     */
    public List<NodeProperties> getNodes() {
        return nodes.get();
    }

    public Mono<List<NodeProperties>> list() {
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(1L), Schedulers.boundedElastic())
                .map(i -> getNodes())
                .filter(list -> !list.isEmpty())
                .next();
    }

    @VisibleForTesting
    Flux<NodeProperties> refresh() {
        final var retry = Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1L))
                .maxBackoff(Duration.ofSeconds(8L))
                .scheduler(refreshScheduler)
                .doBeforeRetry(r -> log.warn(
                        "Retry attempt #{} after failure: {}",
                        r.totalRetries() + 1,
                        r.failure().getMessage()));

        log.info("Refreshing node list");
        var predicate = monitorProperties.getNodeValidation().getTls().getPredicate();
        return Flux.fromIterable(monitorProperties.getNodes())
                .switchIfEmpty(Flux.defer(this::getAddressBook))
                .switchIfEmpty(Flux.defer(() -> Flux.fromIterable(getNetworkNodes())))
                .filter(n -> predicate.test(n.getPort()))
                .switchIfEmpty(Flux.error(new IllegalArgumentException("Nodes must not be empty")))
                .retryWhen(retry);
    }

    private void addNode(final NodeProperties node) {
        nodes.updateAndGet(current -> {
            if (current.contains(node)) {
                return current;
            }

            final var updated = new ArrayList<>(current);
            updated.add(node);
            return List.copyOf(updated);
        });
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

    // Fallback when the mirror node yields no usable nodes or address book retrieval is disabled
    private List<NodeProperties> getNetworkNodes() {
        final var network = monitorProperties.getNetwork();
        final var nodeValidation = monitorProperties.getNodeValidation();
        // Pinned networks only use TLS endpoints, verified against the bundled certificate hash they carry
        final var predicate = isPinned()
                ? TlsMode.TLS.getPredicate()
                : nodeValidation.getTls().getPredicate();
        final var endpointsPerNode = new HashMap<String, Integer>();
        final var networkNodes = new ArrayList<NodeProperties>();

        for (final var node : network.getNodes()) {
            if (!predicate.test(node.getPort())) {
                continue;
            }

            final int count = endpointsPerNode.merge(node.getAccountId(), 1, Integer::sum);
            if (count <= nodeValidation.getMaxEndpointsPerNode()) {
                networkNodes.add(node);
            }
        }

        log.info("Using {} nodes from the SDK's {} address book", networkNodes.size(), network);
        return networkNodes;
    }

    // Nodes on networks with an SDK-bundled address book are authenticated against it rather than the REST response
    private boolean isPinned() {
        final var tls = monitorProperties.getNodeValidation().getTls();
        return (tls == TlsMode.PREFER_TLS || tls == TlsMode.TLS)
                && !monitorProperties.getNetwork().getTrustedCertHashes().isEmpty();
    }

    private boolean isPreferTls(final NetworkNode networkNode) {
        final var nodeValidation = monitorProperties.getNodeValidation();
        // Without validation a broken TLS endpoint would go unnoticed, so keep the plaintext endpoint as before
        return nodeValidation.getTls() == TlsMode.PREFER_TLS
                && nodeValidation.isEnabled()
                // REST reports a missing hash as "0x"
                && StringUtils.isNotBlank(HederaNetwork.normalizeCertHash(networkNode.getNodeCertHash()));
    }

    private NodeProperties newNodeProperties(final NetworkNode networkNode, final ServiceEndpoint serviceEndpoint) {
        final var host = StringUtils.isNotBlank(serviceEndpoint.getDomainName())
                ? serviceEndpoint.getDomainName()
                : serviceEndpoint.getIpAddressV4();
        final var nodeProperties = new NodeProperties();
        nodeProperties.setAccountId(networkNode.getNodeAccountId());
        nodeProperties.setCertHash(Strings.CS.remove(networkNode.getNodeCertHash(), "0x"));
        nodeProperties.setHost(host);
        nodeProperties.setNodeId(networkNode.getNodeId());
        nodeProperties.setPort(serviceEndpoint.getPort());
        return nodeProperties;
    }

    private List<NodeProperties> toNodeProperties(final NetworkNode networkNode) {
        if (isPinned()) {
            return toPinnedNodeProperties(networkNode);
        }

        final var nodeValidation = monitorProperties.getNodeValidation();
        final int maxEndpoints = nodeValidation.getMaxEndpointsPerNode();
        final var tlsModePredicate = nodeValidation.getTls().getPredicate();
        final var endpoints = networkNode.getServiceEndpoints().stream()
                .filter(s -> tlsModePredicate.test(s.getPort()))
                .map(s -> newNodeProperties(networkNode, s))
                .toList();

        if (!isPreferTls(networkNode)) {
            return endpoints.stream().limit(maxEndpoints).toList();
        }

        final var tlsPredicate = TlsMode.TLS.getPredicate();
        final var tlsNodes = endpoints.stream()
                .filter(n -> tlsPredicate.test(n.getPort()))
                .limit(maxEndpoints)
                .toList();

        // A node that publishes TLS is only used over TLS. Plaintext needs an explicit PLAINTEXT or BOTH mode.
        return tlsNodes.isEmpty() ? endpoints.stream().limit(maxEndpoints).toList() : tlsNodes;
    }

    /**
     * Only the endpoints are taken from the REST response. The node must be in the SDK's bundled address book and is
     * only reachable over TLS verified against the bundled certificate hash, so a forged response can hide nodes but
     * cannot add an account or substitute a certificate.
     */
    private List<NodeProperties> toPinnedNodeProperties(final NetworkNode networkNode) {
        final var accountId = networkNode.getNodeAccountId();
        final var network = monitorProperties.getNetwork();
        final var certHash = network.getTrustedCertHashes().get(accountId);
        if (certHash == null) {
            log.warn("Ignoring node {} since it is not in the SDK's {} address book", accountId, network);
            return List.of();
        }

        if (!certHash.equals(HederaNetwork.normalizeCertHash(networkNode.getNodeCertHash()))) {
            log.warn(
                    "Certificate hash of node {} from the mirror node differs from the SDK's {} address book. Using the SDK's",
                    accountId,
                    network);
        }

        final var tlsPredicate = TlsMode.TLS.getPredicate();
        final var nodes = networkNode.getServiceEndpoints().stream()
                .filter(s -> tlsPredicate.test(s.getPort()))
                .limit(monitorProperties.getNodeValidation().getMaxEndpointsPerNode())
                .map(s -> {
                    final var nodeProperties = newNodeProperties(networkNode, s);
                    nodeProperties.setCertHash(certHash);
                    return nodeProperties;
                })
                .toList();
        if (nodes.isEmpty()) {
            log.warn("Ignoring node {} since the mirror node lists no TLS endpoint for it", accountId);
        }

        return nodes;
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
            addNode(node);
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
                addNode(node);
                return true;
            }

            log.warn("Unable to validate node {}: invalid status code {}", node, receiptStatus);
        } catch (TimeoutException e) {
            log.warn("Unable to validate node {}: Timed out", node);
        } catch (Exception e) {
            log.warn("Unable to validate node {}: ", node, e);
        }

        removeNode(node);
        return false;
    }

    private void removeNode(final NodeProperties node) {
        nodes.updateAndGet(current -> {
            if (!current.contains(node)) {
                return current;
            }

            final var updated = new ArrayList<>(current);
            updated.remove(node);
            return List.copyOf(updated);
        });
    }
}
