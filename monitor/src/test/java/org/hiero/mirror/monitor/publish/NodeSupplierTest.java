// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import static com.hedera.hashgraph.sdk.proto.ResponseCodeEnum.FREEZE_UPGRADE_IN_PROGRESS;
import static com.hedera.hashgraph.sdk.proto.ResponseCodeEnum.OK;
import static com.hedera.hashgraph.sdk.proto.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Uninterruptibles;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.proto.CryptoServiceGrpc;
import com.hedera.hashgraph.sdk.proto.Query;
import com.hedera.hashgraph.sdk.proto.Response;
import com.hedera.hashgraph.sdk.proto.ResponseCodeEnum;
import com.hedera.hashgraph.sdk.proto.ResponseHeader;
import com.hedera.hashgraph.sdk.proto.Transaction;
import com.hedera.hashgraph.sdk.proto.TransactionGetReceiptResponse;
import com.hedera.hashgraph.sdk.proto.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.TransactionResponse;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import lombok.Data;
import org.hiero.mirror.monitor.HederaNetwork;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.monitor.NodeProperties;
import org.hiero.mirror.monitor.NodeValidationProperties.TlsMode;
import org.hiero.mirror.monitor.OperatorProperties;
import org.hiero.mirror.monitor.publish.transaction.TransactionType;
import org.hiero.mirror.monitor.subscribe.rest.RestApiClient;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@CustomLog
@ExtendWith(MockitoExtension.class)
final class NodeSupplierTest {

    private static final String SERVER = "test2";
    private static final Duration WAIT = Duration.ofSeconds(4L);

    private CryptoServiceStub cryptoServiceStub;
    private MonitorProperties monitorProperties;
    private NetworkNode networkNode;
    private NodeProperties node;
    private NodeSupplier nodeSupplier;
    private PublishScenarioProperties publishScenarioProperties;
    private Server server;

    @Mock
    private RestApiClient restApiClient;

    @BeforeEach
    void setup() throws IOException {
        node = new NodeProperties("0.0.3", "in-process:" + SERVER);
        node.setNodeId(0L);
        networkNode = new NetworkNode();
        networkNode.setNodeAccountId(node.getAccountId());
        networkNode.addServiceEndpointsItem(
                new ServiceEndpoint().ipAddressV4(node.getEndpoint()).port(50211));
        networkNode.addServiceEndpointsItem(
                new ServiceEndpoint().ipAddressV4(node.getEndpoint()).port(50212));

        publishScenarioProperties = new PublishScenarioProperties();
        publishScenarioProperties.setName("test");
        publishScenarioProperties.setType(TransactionType.CRYPTO_TRANSFER);
        monitorProperties = new MonitorProperties();
        monitorProperties.setNodes(Set.of(node));
        OperatorProperties operatorProperties = monitorProperties.getOperator();
        operatorProperties.setAccountId("0.0.100");
        operatorProperties.setPrivateKey(PrivateKey.generateED25519().toString());

        nodeSupplier = new NodeSupplier(monitorProperties, restApiClient);
        cryptoServiceStub = new CryptoServiceStub();
        server = InProcessServerBuilder.forName(SERVER)
                .addService(cryptoServiceStub)
                .directExecutor()
                .build()
                .start();
    }

    @AfterEach
    void teardown() throws InterruptedException {
        cryptoServiceStub.verify();
        if (server != null) {
            server.shutdown();
            server.awaitTermination();
        }
    }

    @Test
    void get() {
        monitorProperties.getNodeValidation().setEnabled(false);
        nodeSupplier.validateNode(node);
        assertThat(nodeSupplier.get()).isEqualTo(node);
    }

    @Test
    void getNoValidNodes() {
        assertThatThrownBy(() -> nodeSupplier.get())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No valid nodes available");
    }

    @Test
    void list() {
        monitorProperties.getNodeValidation().setEnabled(false);
        nodeSupplier.validateNode(node);
        assertThat(nodeSupplier.list().block()).containsExactly(node);
    }

    @Test
    void listEmptyThenPopulated() {
        monitorProperties.getNodeValidation().setEnabled(false);
        StepVerifier.withVirtualTime(() -> nodeSupplier.list()).thenAwait(WAIT).expectNoEvent(WAIT);
        nodeSupplier.validateNode(node);
        assertThat(nodeSupplier.list().block(WAIT)).containsExactly(node);
    }

    @Test
    void init() {
        cryptoServiceStub.addQuery(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransaction(Mono.just(response(OK)));
        nodeSupplier.init();
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        assertThat(nodeSupplier.get()).isEqualTo(node);
    }

    @Test
    void initWithRetry() {
        monitorProperties.getNodeValidation().setRetryBackoff(Duration.ofMillis(100L));
        cryptoServiceStub
                .addTransaction(Mono.just(response(FREEZE_UPGRADE_IN_PROGRESS)))
                .addTransaction(Mono.just(response(FREEZE_UPGRADE_IN_PROGRESS)))
                .addTransaction(Mono.just(response(OK)));
        cryptoServiceStub.addQuery(Mono.just(receipt(SUCCESS)));
        nodeSupplier.init();
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        assertThat(nodeSupplier.get()).isEqualTo(node);
    }

    @Test
    void refreshCustomNodes() {
        StepVerifier.withVirtualTime(() -> nodeSupplier.refresh())
                .thenAwait(WAIT)
                .expectNext(node)
                .expectComplete()
                .verify(WAIT);

        // Nodes are not exposed to publishers before validation
        assertThat(nodeSupplier.getNodes()).isEmpty();
    }

    @Test
    void refreshAddressBook() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.getNodeValidation().setTls(TlsMode.PLAINTEXT);
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(1)
                .first()
                .returns(networkNode.getNodeAccountId(), NodeProperties::getAccountId)
                .returns(networkNode.getServiceEndpoints().get(0).getIpAddressV4(), NodeProperties::getHost)
                .returns(50211, NodeProperties::getPort);
        assertThat(nodeSupplier.getNodes()).isEmpty();
    }

    @Test
    void refreshAddressBookTls() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.getNodeValidation().setTls(TlsMode.TLS);
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(1)
                .first()
                .returns(networkNode.getNodeAccountId(), NodeProperties::getAccountId)
                .returns(networkNode.getServiceEndpoints().get(0).getIpAddressV4(), NodeProperties::getHost)
                .returns(50212, NodeProperties::getPort);
        assertThat(nodeSupplier.getNodes()).isEmpty();
    }

    @Test
    void refreshAddressBookBoth() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.getNodeValidation().setTls(TlsMode.BOTH);
        monitorProperties.getNodeValidation().setMaxEndpointsPerNode(Integer.MAX_VALUE);
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(2)
                .allSatisfy(n -> assertThat(n)
                        .returns(networkNode.getNodeAccountId(), NodeProperties::getAccountId)
                        .returns(networkNode.getServiceEndpoints().get(0).getIpAddressV4(), NodeProperties::getHost))
                .extracting(NodeProperties::getPort)
                .containsExactlyInAnyOrder(50211, 50212);
    }

    @Test
    void maxEndpointsPerNode() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.getNodeValidation().setTls(TlsMode.BOTH);
        monitorProperties.getNodeValidation().setMaxEndpointsPerNode(1);
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(1)
                .allSatisfy(n -> assertThat(n).returns(networkNode.getNodeAccountId(), NodeProperties::getAccountId))
                .extracting(NodeProperties::getPort)
                .containsAnyOf(50211, 50212);
    }

    @Test
    void refreshAddressBookPreferTls() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.setNetwork(HederaNetwork.OTHER);
        networkNode.setNodeCertHash("0x01");
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(1)
                .first()
                .returns(networkNode.getNodeAccountId(), NodeProperties::getAccountId)
                .returns("01", NodeProperties::getCertHash)
                .returns(50212, NodeProperties::getPort);

        // Neither endpoint is exposed to publishers before validation
        assertThat(nodeSupplier.getNodes()).isEmpty();
    }

    @NullSource
    @ParameterizedTest
    @ValueSource(strings = {"", "0x"})
    void refreshAddressBookPreferTlsNoCertHash(final String certHash) {
        monitorProperties.setNodes(Set.of());
        monitorProperties.setNetwork(HederaNetwork.OTHER);
        networkNode.setNodeCertHash(certHash);
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(1)
                .first()
                .returns(50211, NodeProperties::getPort);
    }

    @Test
    void refreshAddressBookPreferTlsOnlyPlaintext() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.setNetwork(HederaNetwork.OTHER);
        networkNode.setNodeCertHash("0x01");
        networkNode.getServiceEndpoints().removeIf(s -> s.getPort() == 50212);
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(1)
                .first()
                .returns(50211, NodeProperties::getPort);
    }

    @Test
    void refreshAddressBookPreferTlsOnlyTls() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.setNetwork(HederaNetwork.OTHER);
        networkNode.setNodeCertHash("0x01");
        networkNode.getServiceEndpoints().removeIf(s -> s.getPort() == 50211);
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(1)
                .first()
                .returns(50212, NodeProperties::getPort);
        assertThat(nodeSupplier.getNodes()).isEmpty();
    }

    @Test
    void refreshAddressBookPreferTlsValidationDisabled() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.setNetwork(HederaNetwork.OTHER);
        monitorProperties.getNodeValidation().setEnabled(false);
        networkNode.setNodeCertHash("0x01");
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(1)
                .first()
                .returns(50211, NodeProperties::getPort);
    }

    @Test
    @Timeout(3)
    void validateNodePreferTls() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.setNetwork(HederaNetwork.OTHER);
        networkNode.setNodeCertHash("0x01");
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        final var tlsNode = nodeSupplier.refresh().blockFirst();
        assertThat(tlsNode).returns(50212, NodeProperties::getPort);

        cryptoServiceStub.addQuery(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransaction(Mono.just(response(OK)));
        assertThat(nodeSupplier.validateNode(tlsNode)).isTrue();

        assertThat(nodeSupplier.getNodes()).containsExactly(tlsNode);
    }

    @Test
    @Timeout(3)
    void validateNodePreferTlsNoDowngrade() throws Exception {
        final var tlsServerName = "tls";
        final var tlsCryptoServiceStub = new CryptoServiceStub();
        final var tlsServer = InProcessServerBuilder.forName(tlsServerName)
                .addService(tlsCryptoServiceStub)
                .directExecutor()
                .build()
                .start();

        try {
            monitorProperties.setNodes(Set.of());
            monitorProperties.setNetwork(HederaNetwork.OTHER);
            networkNode.setNodeCertHash("0x01");
            networkNode.getServiceEndpoints().get(1).setIpAddressV4("in-process:" + tlsServerName);
            when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
            final var tlsNode = nodeSupplier.refresh().blockFirst();
            assertThat(tlsNode).returns(50212, NodeProperties::getPort);

            // TLS endpoint fails validation and the plaintext endpoint is never contacted
            tlsCryptoServiceStub.addTransaction(Mono.just(response(FREEZE_UPGRADE_IN_PROGRESS)));
            assertThat(nodeSupplier.validateNode(tlsNode)).isFalse();
            assertThat(nodeSupplier.getNodes()).isEmpty();
        } finally {
            tlsCryptoServiceStub.verify();
            tlsServer.shutdownNow();
            tlsServer.awaitTermination();
        }
    }

    @Test
    void refreshAddressBookPinned() {
        monitorProperties.setNodes(Set.of());
        networkNode.setNodeCertHash("0x01");
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        final var trustedCertHash = HederaNetwork.TESTNET.getTrustedCertHashes().get(node.getAccountId());

        // The bundled certificate hash replaces the one from the REST response
        assertThat(nodeSupplier.refresh().collectList().block())
                .singleElement()
                .returns(node.getAccountId(), NodeProperties::getAccountId)
                .returns(50212, NodeProperties::getPort)
                .returns(trustedCertHash, NodeProperties::getCertHash);
    }

    @Test
    void refreshAddressBookPinnedUnknownNode() {
        monitorProperties.setNodes(Set.of());
        networkNode.setNodeAccountId("0.0.999");
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));

        // The unknown account is rejected, leaving only the SDK's bundled network nodes
        final var nodes = nodeSupplier.refresh().collectList().block();
        assertPinnedNetworkNodes(nodes);
        assertThat(nodes)
                .noneMatch(
                        n -> n.getAccountId().equals("0.0.999") || n.getHost().startsWith("in-process:"));
    }

    @Test
    void refreshAddressBookPinnedOnlyPlaintext() {
        monitorProperties.setNodes(Set.of());
        networkNode.setNodeCertHash("0x01");
        networkNode.getServiceEndpoints().removeIf(s -> s.getPort() == 50212);
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));

        // A node advertised without a TLS endpoint is rejected instead of being used over plaintext
        final var nodes = nodeSupplier.refresh().collectList().block();
        assertPinnedNetworkNodes(nodes);
        assertThat(nodes)
                .noneMatch(
                        n -> n.getAccountId().equals("0.0.999") || n.getHost().startsWith("in-process:"));
    }

    @Test
    void validateNodePinnedTlsFails() {
        monitorProperties.setNodes(Set.of());
        networkNode.setNodeCertHash("0x01");
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        final var tlsNode = nodeSupplier.refresh().blockFirst();

        // TLS fails and the plaintext endpoint is not used for a pinned node
        cryptoServiceStub.addTransaction(Mono.just(response(FREEZE_UPGRADE_IN_PROGRESS)));
        assertThat(nodeSupplier.validateNode(tlsNode)).isFalse();
        assertThat(nodeSupplier.getNodes()).isEmpty();
    }

    @Test
    void refreshAddressBookPinnedNotAppliedWhenPlaintext() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.getNodeValidation().setTls(TlsMode.PLAINTEXT);
        networkNode.setNodeAccountId("0.0.999");
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));

        // An explicit insecure mode keeps the previous behaviour
        assertThat(nodeSupplier.refresh().collectList().block())
                .singleElement()
                .returns("0.0.999", NodeProperties::getAccountId)
                .returns(50211, NodeProperties::getPort);
    }

    @Test
    void maxNodes() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.getNodeValidation().setTls(TlsMode.PLAINTEXT);
        monitorProperties.getNodeValidation().setMaxEndpointsPerNode(1);
        var networkNode2 = new NetworkNode();
        networkNode2.setNodeAccountId("0.0.4");
        networkNode2.addServiceEndpointsItem(
                new ServiceEndpoint().ipAddressV4("localhost").port(50211));

        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode, networkNode2));
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(2)
                .extracting(NodeProperties::getAccountId)
                .containsExactlyInAnyOrder(node.getAccountId(), networkNode2.getNodeAccountId());
    }

    @Test
    void refreshAddressBookRetryError() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.getNodeValidation().setTls(TlsMode.PLAINTEXT);
        when(restApiClient.getNodes())
                .thenReturn(Flux.error(new ConnectException("connection refused")))
                .thenReturn(Flux.just(networkNode));
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(1)
                .first()
                .returns(networkNode.getNodeAccountId(), NodeProperties::getAccountId)
                .returns(networkNode.getServiceEndpoints().get(0).getIpAddressV4(), NodeProperties::getHost);
        assertThat(nodeSupplier.getNodes()).isEmpty();
    }

    @Test
    void refreshAddressBookNoEndpoints() {
        monitorProperties.setNodes(Set.of());
        networkNode.getServiceEndpoints().clear();
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        assertPinnedNetworkNodes(nodeSupplier.refresh().collectList().block());
    }

    @Test
    void refreshWithNetwork() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.getNodeValidation().setRetrieveAddressBook(false);
        assertPinnedNetworkNodes(nodeSupplier.refresh().collectList().block());
    }

    @Test
    void refreshWithNetworkPlaintext() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.getNodeValidation().setRetrieveAddressBook(false);
        monitorProperties.getNodeValidation().setTls(TlsMode.PLAINTEXT);
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(HederaNetwork.TESTNET.getTrustedCertHashes().size())
                .allSatisfy(n -> assertThat(n)
                        .returns(50211, NodeProperties::getPort)
                        .extracting(NodeProperties::getHost)
                        .asString()
                        .doesNotContain(":"));
    }

    @Test
    void refreshWithNetworkMaxEndpointsPerNode() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.getNodeValidation().setRetrieveAddressBook(false);
        monitorProperties.getNodeValidation().setMaxEndpointsPerNode(2);
        assertThat(nodeSupplier.refresh().collectList().block())
                .hasSize(2 * HederaNetwork.TESTNET.getTrustedCertHashes().size())
                .allSatisfy(n -> assertThat(n).returns(50212, NodeProperties::getPort));
    }

    @Test
    void refreshNotFound() {
        monitorProperties.setNetwork(HederaNetwork.OTHER);
        monitorProperties.setNodes(Set.of());
        StepVerifier.withVirtualTime(() -> nodeSupplier.refresh()).expectNoEvent(WAIT);
    }

    @Test
    @Timeout(3)
    void validationRecovers() {
        // Given node validated as bad
        cryptoServiceStub.addTransaction(Mono.just(response(FREEZE_UPGRADE_IN_PROGRESS)));
        assertThat(nodeSupplier.validateNode(node)).isFalse();
        assertThatThrownBy(() -> nodeSupplier.get())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No valid nodes available");

        // When it recovers
        cryptoServiceStub.addQuery(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransaction(Mono.just(response(OK)));
        assertThat(nodeSupplier.validateNode(node)).isTrue();

        // Then it is marked as healthy
        assertThat(nodeSupplier.get()).isEqualTo(node);
    }

    @Test
    @Timeout(3)
    void someValidNodes() throws Exception {
        var server3 = "server3";
        var cryptoServiceStub2 = new CryptoServiceStub();
        var server2 = InProcessServerBuilder.forName(server3)
                .addService(cryptoServiceStub2)
                .directExecutor()
                .build()
                .start();

        try {
            var node2 = new NodeProperties("0.0.4", "in-process:" + server3);
            node2.setNodeId(1L);
            monitorProperties.setNodes(Set.of(node, node2));

            // Validate good node
            cryptoServiceStub.addQuery(Mono.just(receipt(SUCCESS)));
            cryptoServiceStub.addTransaction(Mono.just(response(OK)));
            assertThat(nodeSupplier.validateNode(node)).isTrue();

            // Validate bad node
            cryptoServiceStub2.addQuery(Mono.just(receipt(FREEZE_UPGRADE_IN_PROGRESS)));
            cryptoServiceStub2.addTransaction(Mono.just(response(OK)));
            assertThat(nodeSupplier.validateNode(node2)).isFalse();

            // Then only good node returned
            for (int i = 0; i < 10; ++i) {
                assertThat(nodeSupplier.get()).isEqualTo(node);
            }
        } finally {
            cryptoServiceStub2.verify();
            server2.shutdownNow();
            server2.awaitTermination();
        }
    }

    @Test
    void getNodesSameInstanceUntilChanged() {
        monitorProperties.getNodeValidation().setEnabled(false);
        nodeSupplier.validateNode(node);
        final var snapshot = nodeSupplier.getNodes();
        nodeSupplier.validateNode(node);
        assertThat(nodeSupplier.getNodes()).isSameAs(snapshot).containsExactly(node);
    }

    @Test
    @Timeout(3)
    void validationSucceeds() {
        cryptoServiceStub.addQuery(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransaction(Mono.just(response(OK)));
        assertThat(nodeSupplier.validateNode(node)).isTrue();
    }

    private void assertPinnedNetworkNodes(final List<NodeProperties> nodes) {
        // Every SDK fallback node is a TLS endpoint carrying its bundled hash, with host and port kept separate
        final var trustedCertHashes = HederaNetwork.TESTNET.getTrustedCertHashes();
        assertThat(nodes)
                .hasSize(trustedCertHashes.size())
                .allSatisfy(n -> assertThat(n)
                        .returns(50212, NodeProperties::getPort)
                        .returns(trustedCertHashes.get(n.getAccountId()), NodeProperties::getCertHash)
                        .extracting(NodeProperties::getHost)
                        .asString()
                        .doesNotContain(":"))
                .extracting(NodeProperties::getAccountId)
                .doesNotHaveDuplicates();
    }

    private Response receipt(ResponseCodeEnum responseCode) {
        ResponseHeader responseHeader =
                ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(OK).build();
        return Response.newBuilder()
                .setTransactionGetReceipt(TransactionGetReceiptResponse.newBuilder()
                        .setHeader(responseHeader)
                        .setReceipt(TransactionReceipt.newBuilder()
                                .setStatus(responseCode)
                                .build())
                        .build())
                .build();
    }

    private TransactionResponse response(ResponseCodeEnum responseCode) {
        return TransactionResponse.newBuilder()
                .setNodeTransactionPrecheckCode(responseCode)
                .build();
    }

    @Data
    private class CryptoServiceStub extends CryptoServiceGrpc.CryptoServiceImplBase {

        private Queue<Mono<Response>> queries = new ConcurrentLinkedQueue<>();
        private Queue<Mono<TransactionResponse>> transactions = new ConcurrentLinkedQueue<>();

        CryptoServiceStub addQuery(Mono<Response> query) {
            queries.add(query);
            return this;
        }

        CryptoServiceStub addTransaction(Mono<TransactionResponse> transaction) {
            transactions.add(transaction);
            return this;
        }

        @Override
        public void cryptoTransfer(Transaction request, StreamObserver<TransactionResponse> responseObserver) {
            log.debug("cryptoTransfer: {}", request);
            send(responseObserver, transactions.poll());
        }

        @Override
        public void getTransactionReceipts(Query request, StreamObserver<Response> responseObserver) {
            log.debug("getTransactionReceipts: {}", request);
            send(responseObserver, queries.poll());
        }

        private <T> void send(StreamObserver<T> responseObserver, Mono<T> response) {
            assertThat(response).isNotNull();
            response.delayElement(Duration.ofMillis(100L))
                    .doOnError(responseObserver::onError)
                    .doOnNext(responseObserver::onNext)
                    .doOnNext(t -> log.trace("Next: {}", t))
                    .doOnSuccess(r -> responseObserver.onCompleted())
                    .subscribe();
        }

        void verify() {
            assertThat(queries).isEmpty();
            assertThat(transactions).isEmpty();
        }
    }
}
