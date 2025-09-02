// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import static com.hedera.hashgraph.sdk.proto.ResponseCodeEnum.OK;
import static com.hedera.hashgraph.sdk.proto.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.hashgraph.sdk.proto.CryptoServiceGrpc;
import com.hedera.hashgraph.sdk.proto.Query;
import com.hedera.hashgraph.sdk.proto.Response;
import com.hedera.hashgraph.sdk.proto.ResponseCodeEnum;
import com.hedera.hashgraph.sdk.proto.ResponseHeader;
import com.hedera.hashgraph.sdk.proto.Transaction;
import com.hedera.hashgraph.sdk.proto.TransactionGetReceiptResponse;
import com.hedera.hashgraph.sdk.proto.TransactionGetRecordResponse;
import com.hedera.hashgraph.sdk.proto.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.TransactionRecord;
import com.hedera.hashgraph.sdk.proto.TransactionResponse;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Queue;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import lombok.CustomLog;
import lombok.Data;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.monitor.NodeProperties;
import org.hiero.mirror.monitor.OperatorProperties;
import org.hiero.mirror.monitor.publish.transaction.TransactionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@CustomLog
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionPublisherTest {

    private static final String SERVER = "test1";

    private CryptoServiceStub cryptoServiceStub;
    private PublishProperties publishProperties;
    private PublishScenarioProperties publishScenarioProperties;
    private Server server;
    private TransactionPublisher transactionPublisher;

    @Mock
    private NodeSupplier nodeSupplier;

    @BeforeEach
    void setup() throws IOException {
        publishScenarioProperties = new PublishScenarioProperties();
        publishScenarioProperties.setName("test");
        publishScenarioProperties.setType(TransactionType.CRYPTO_TRANSFER);
        var node = new NodeProperties("0.0.3", "in-process:" + SERVER);
        node.setNodeId(0L);
        var duplicate = new NodeProperties("0.0.4", "in-process:" + SERVER);
        duplicate.setNodeId(1L);
        var nodes = new TreeSet<>(Comparator.comparing(NodeProperties::getNodeId));
        nodes.add(node);
        nodes.add(duplicate);
        var monitorProperties = new MonitorProperties();
        monitorProperties.setNodes(nodes);
        monitorProperties.getNodeValidation().setEnabled(false);
        OperatorProperties operatorProperties = monitorProperties.getOperator();
        operatorProperties.setAccountId("0.0.100");
        operatorProperties.setPrivateKey(PrivateKey.generateED25519().toString());
        publishProperties = new PublishProperties();
        transactionPublisher = new TransactionPublisher(monitorProperties, nodeSupplier, publishProperties);
        cryptoServiceStub = new CryptoServiceStub();
        when(nodeSupplier.refresh()).thenReturn(Flux.fromIterable(monitorProperties.getNodes()));
        when(nodeSupplier.get()).thenReturn(node);
        server = InProcessServerBuilder.forName(SERVER)
                .addService(cryptoServiceStub)
                .directExecutor()
                .build()
                .start();
    }

    @AfterEach
    void teardown() throws InterruptedException {
        cryptoServiceStub.verify();
        transactionPublisher.close();
        if (server != null) {
            server.shutdown();
            server.awaitTermination();
        }
    }

    @Test
    @Timeout(3)
    void publish() {
        PublishRequest request = request().build();
        cryptoServiceStub.addTransaction(Mono.just(response(OK)));

        transactionPublisher
                .publish(request)
                .as(StepVerifier::create)
                .expectNextMatches(r -> {
                    assertThat(r)
                            .isNotNull()
                            .returns(request, PublishResponse::getRequest)
                            .returns(request.getTransaction().getTransactionId(), PublishResponse::getTransactionId)
                            .extracting(PublishResponse::getTimestamp)
                            .isNotNull();
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishWithLogResponse() {
        publishScenarioProperties.setLogResponse(true);
        cryptoServiceStub.addTransaction(Mono.just(response(OK)));

        transactionPublisher
                .publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishWithReceipt() {
        cryptoServiceStub.addQuery(Mono.just(receipt(SUCCESS)));
        cryptoServiceStub.addTransaction(Mono.just(response(OK)));

        transactionPublisher
                .publish(request().receipt(true).build())
                .as(StepVerifier::create)
                .expectNextMatches(r -> {
                    assertThat(r).extracting(PublishResponse::getReceipt).isNotNull();
                    assertThat(r)
                            .extracting(PublishResponse::getTransactionRecord)
                            .isNull();
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishWithRecord() {
        cryptoServiceStub.addQuery(Mono.just(txnRecord(SUCCESS)));
        cryptoServiceStub.addTransaction(Mono.just(response(OK)));

        transactionPublisher
                .publish(request().sendRecord(true).build())
                .as(StepVerifier::create)
                .expectNextMatches(r -> {
                    assertThat(r).extracting(PublishResponse::getReceipt).isNotNull();
                    assertThat(r)
                            .extracting(PublishResponse::getTransactionRecord)
                            .isNotNull();
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishPreCheckError() {
        ResponseCodeEnum errorResponseCode = ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
        cryptoServiceStub.addTransaction(Mono.just(response(errorResponseCode)));

        transactionPublisher
                .publish(request().build())
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t)
                        .isInstanceOf(PublishException.class)
                        .hasMessageContaining(errorResponseCode.toString()))
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishRetrySuccessful() {
        cryptoServiceStub
                .addTransaction(Mono.just(response(ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED)))
                .addTransaction(Mono.just(response(ResponseCodeEnum.OK)));

        transactionPublisher
                .publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishRetryError() {
        ResponseCodeEnum errorResponseCode = ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
        cryptoServiceStub
                .addTransaction(Mono.just(response(errorResponseCode)))
                .addTransaction(Mono.just(response(errorResponseCode)));

        transactionPublisher
                .publish(request().build())
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t)
                        .isInstanceOf(PublishException.class)
                        .hasMessageContaining("exceeded maximum attempts for request with last exception being")
                        .rootCause()
                        .hasMessageContaining(errorResponseCode.toString()))
                .verify(Duration.ofSeconds(2L));
    }

    @Test
    @Timeout(3)
    void publishRetrySameRequest() {
        ResponseCodeEnum errorResponseCode = ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
        cryptoServiceStub
                .addTransaction(Mono.just(response(errorResponseCode)))
                .addTransaction(Mono.just(response(errorResponseCode)));

        var request = request().build();
        transactionPublisher
                .publish(request)
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t)
                        .isInstanceOf(PublishException.class)
                        .hasMessageContaining("exceeded maximum attempts for request with last exception being")
                        .rootCause()
                        .hasMessageContaining(errorResponseCode.toString()))
                .verify(Duration.ofSeconds(2L));

        cryptoServiceStub.addTransaction(Mono.just(response(OK)));
        transactionPublisher
                .publish(request)
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishTimeout() {
        publishScenarioProperties.setTimeout(Duration.ofMillis(100L));
        cryptoServiceStub.addTransaction(Mono.delay(Duration.ofMillis(500L)).thenReturn(response(OK)));

        transactionPublisher
                .publish(request().build())
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t)
                        .isInstanceOf(PublishException.class)
                        .hasMessageContaining("Did not observe any item or terminal signal within 100ms")
                        .hasCauseInstanceOf(TimeoutException.class))
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void publishNoValidNodes() {
        PublishRequest request = request().build();
        when(nodeSupplier.get()).thenThrow(new IllegalArgumentException("No valid nodes"));

        transactionPublisher
                .publish(request)
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t)
                        .isInstanceOf(PublishException.class)
                        .hasCauseInstanceOf(IllegalArgumentException.class))
                .verify(Duration.ofSeconds(1L));
    }

    @Test
    @Timeout(3)
    void closeWhenDisabled() {
        publishProperties.setEnabled(false);
        transactionPublisher.close();
        transactionPublisher
                .publish(request().build())
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));
    }

    private PublishRequest.PublishRequestBuilder request() {
        return PublishRequest.builder()
                .scenario(new PublishScenario(publishScenarioProperties))
                .timestamp(Instant.now())
                .transaction(new TransferTransaction().setMaxAttempts(2));
    }

    private TransactionResponse response(ResponseCodeEnum responseCode) {
        return TransactionResponse.newBuilder()
                .setNodeTransactionPrecheckCode(responseCode)
                .build();
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

    private Response txnRecord(ResponseCodeEnum responseCode) {
        ResponseHeader.Builder responseHeader = ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(OK);
        TransactionReceipt.Builder transactionReceipt =
                TransactionReceipt.newBuilder().setStatus(responseCode);
        return Response.newBuilder()
                .setTransactionGetRecord(TransactionGetRecordResponse.newBuilder()
                        .setHeader(responseHeader)
                        .setTransactionRecord(TransactionRecord.newBuilder().setReceipt(transactionReceipt)))
                .build();
    }

    @Data
    private class CryptoServiceStub extends CryptoServiceGrpc.CryptoServiceImplBase {

        private Queue<Mono<TransactionResponse>> transactions = new ConcurrentLinkedQueue<>();
        private Queue<Mono<Response>> queries = new ConcurrentLinkedQueue<>();

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

        @Override
        public void getTxRecordByTxID(Query request, StreamObserver<Response> responseObserver) {
            log.debug("getTxRecordByTxID: {}", request);
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
