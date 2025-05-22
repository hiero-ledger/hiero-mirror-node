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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@CustomLog
@ExtendWith(MockitoExtension.class)
class NodeSupplierTest {

    private static final String SERVER = "test2";
    private static final Duration WAIT = Duration.ofSeconds(10L);

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
        assertThat(nodeSupplier.get()).isEqualTo(node);
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
                .returns(50211, NodeProperties::getPort)
                .satisfies(n -> assertThat(nodeSupplier.get()).isEqualTo(n));
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
                .returns(50212, NodeProperties::getPort)
                .satisfies(n -> assertThat(nodeSupplier.get()).isEqualTo(n));
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
                .returns(networkNode.getServiceEndpoints().get(0).getIpAddressV4(), NodeProperties::getHost)
                .satisfies(n -> assertThat(nodeSupplier.get()).isEqualTo(n));
    }

    @Test
    void refreshAddressBookNoEndpoints() {
        monitorProperties.setNodes(Set.of());
        networkNode.getServiceEndpoints().clear();
        when(restApiClient.getNodes()).thenReturn(Flux.just(networkNode));
        StepVerifier.withVirtualTime(() -> nodeSupplier.refresh())
                .thenAwait(WAIT)
                .expectNextSequence(monitorProperties.getNetwork().getNodes())
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void refreshWithNetwork() {
        monitorProperties.setNodes(Set.of());
        monitorProperties.getNodeValidation().setRetrieveAddressBook(false);
        StepVerifier.withVirtualTime(() -> nodeSupplier.refresh())
                .thenAwait(WAIT)
                .expectNextSequence(monitorProperties.getNetwork().getNodes())
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void refreshNotFound() {
        monitorProperties.setNetwork(HederaNetwork.OTHER);
        monitorProperties.setNodes(Set.of());
        when(restApiClient.getNodes()).thenReturn(Flux.empty());
        StepVerifier.withVirtualTime(() -> nodeSupplier.refresh())
                .thenAwait(WAIT)
                .expectError(IllegalArgumentException.class)
                .verify(WAIT);
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
    @Timeout(3)
    void validationSucceeds() {
        cryptoServiceStub.addQuery(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransaction(Mono.just(response(OK)));
        assertThat(nodeSupplier.validateNode(node)).isTrue();
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
