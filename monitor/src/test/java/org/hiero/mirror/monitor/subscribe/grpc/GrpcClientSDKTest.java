// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.proto.Timestamp;
import com.hedera.hashgraph.sdk.proto.TopicID;
import com.hedera.hashgraph.sdk.proto.mirror.ConsensusServiceGrpc;
import com.hedera.hashgraph.sdk.proto.mirror.ConsensusTopicQuery;
import com.hedera.hashgraph.sdk.proto.mirror.ConsensusTopicResponse;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import lombok.Data;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.monitor.subscribe.SubscribeProperties;
import org.hiero.mirror.monitor.subscribe.SubscribeResponse;
import org.hiero.mirror.monitor.util.Utility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@CustomLog
class GrpcClientSDKTest {

    private static final Instant START_TIME = Instant.now();
    private static final Duration WAIT = Duration.ofSeconds(10L);
    private static final Duration TOPIC_RESPONSE_DELAY = Duration.ofMillis(200L);

    private ConsensusServiceStub consensusServiceStub;
    private GrpcClientSDK grpcClientSDK;
    private GrpcSubscriberProperties properties;
    private Server server;
    private GrpcSubscription subscription;

    @BeforeEach
    void setup(TestInfo testInfo) throws Exception {
        log.info("Executing: {}", testInfo.getDisplayName());
        properties = new GrpcSubscriberProperties();
        properties.setName(testInfo.getDisplayName());
        properties.setTopicId("0.0.1000");
        subscription = new GrpcSubscription(1, properties);
        var monitorProperties = new MonitorProperties();
        monitorProperties.getMirrorNode().getGrpc().setHost("in-process:test");
        grpcClientSDK = new GrpcClientSDK(monitorProperties, new SubscribeProperties());

        consensusServiceStub = new ConsensusServiceStub();
        server = InProcessServerBuilder.forName("test")
                .addService(consensusServiceStub)
                .build()
                .start();
    }

    @AfterEach
    void teardown() throws Exception {
        grpcClientSDK.close();
        if (server != null) {
            server.shutdown();
            server.awaitTermination();
        }
    }

    @Test
    void subscribe() {
        List<ConsensusTopicResponse> responses = Arrays.asList(response(1L), response(2L));

        consensusServiceStub.setResponses(Flux.fromIterable(responses));
        StepVerifier.Step<SubscribeResponse> stepVerifier =
                StepVerifier.withVirtualTime(() -> grpcClientSDK.subscribe(subscription));

        verifyResponses(stepVerifier, responses);

        assertThat(subscription)
                .returns(2L, GrpcSubscription::getCount)
                .returns(Map.of(), GrpcSubscription::getErrors)
                .extracting(GrpcSubscription::getStopwatch)
                .matches(s -> !s.isRunning());
        assertThat(subscription.getLast()).get().matches(p -> p.sequenceNumber == 2L);
    }

    @Test
    void multipleSubscriptions() {
        List<ConsensusTopicResponse> responses = Arrays.asList(response(1L), response(2L));
        consensusServiceStub.setResponses(Flux.fromIterable(responses));

        StepVerifier.Step<SubscribeResponse> stepVerifier =
                StepVerifier.withVirtualTime(() -> grpcClientSDK.subscribe(subscription));

        GrpcSubscription subscription2 = new GrpcSubscription(2, properties);
        StepVerifier.Step<SubscribeResponse> stepVerifier2 =
                StepVerifier.withVirtualTime(() -> grpcClientSDK.subscribe(subscription2));

        verifyResponses(stepVerifier, responses);
        verifyResponses(stepVerifier2, responses);

        assertThat(subscription).returns(2L, GrpcSubscription::getCount).returns(Map.of(), GrpcSubscription::getErrors);
        assertThat(subscription2)
                .returns(2L, GrpcSubscription::getCount)
                .returns(Map.of(), GrpcSubscription::getErrors);
    }

    @Test
    void resubscribe() {
        properties.setLimit(2L);
        consensusServiceStub.getRequest().setLimit(2L);
        ConsensusTopicResponse response1 = response(1L);
        consensusServiceStub.setResponses(Flux.just(response1).delayElements(Duration.ofSeconds(1L)));
        StepVerifier.withVirtualTime(() -> grpcClientSDK.subscribe(subscription))
                .thenAwait(WAIT)
                .expectNextCount(1L)
                .expectComplete()
                .verify(WAIT);

        Timestamp consensusTimestamp = response1.getConsensusTimestamp();
        consensusServiceStub
                .getRequest()
                .setConsensusStartTime(consensusTimestamp.toBuilder().setNanos(consensusTimestamp.getNanos() + 1));
        consensusServiceStub.getRequest().setLimit(1L);
        consensusServiceStub.setResponses(Flux.just(response(2L)));
        StepVerifier.withVirtualTime(() -> grpcClientSDK.subscribe(subscription))
                .thenAwait(WAIT)
                .expectNextCount(1L)
                .expectComplete()
                .verify(WAIT);
        assertThat(subscription).returns(2L, GrpcSubscription::getCount).returns(Map.of(), GrpcSubscription::getErrors);
    }

    @Test
    void missingPublishTimestamp() {
        consensusServiceStub.setResponses(
                Flux.just(ConsensusTopicResponse.newBuilder().build()));
        StepVerifier.withVirtualTime(() -> grpcClientSDK.subscribe(subscription))
                .thenAwait(WAIT)
                .expectNextCount(1L)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void outOfSequence() {
        List<ConsensusTopicResponse> responses = Arrays.asList(response(1L), response(3L));
        consensusServiceStub.setResponses(Flux.fromIterable(responses));

        verifyResponses(StepVerifier.withVirtualTime(() -> grpcClientSDK.subscribe(subscription)), responses);
    }

    @Test
    void error() {
        consensusServiceStub.setResponses(Flux.error(new StatusRuntimeException(Status.NOT_FOUND)));
        StepVerifier.withVirtualTime(() -> grpcClientSDK.subscribe(subscription))
                .thenAwait(WAIT)
                .expectError(StatusRuntimeException.class)
                .verify(WAIT);
        assertThat(subscription)
                .returns(0L, GrpcSubscription::getCount)
                .extracting(GrpcSubscription::getErrors)
                .matches(ms -> ms.get(Status.NOT_FOUND.getCode().toString()) == 1);
    }

    @Test
    void noMessages() {
        consensusServiceStub.setResponses(Flux.empty());
        StepVerifier.withVirtualTime(() -> grpcClientSDK.subscribe(subscription))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);
        assertThat(subscription)
                .returns(0L, GrpcSubscription::getCount)
                .returns(Map.of(), GrpcSubscription::getErrors)
                .extracting(GrpcSubscription::getStopwatch)
                .matches(s -> !s.isRunning());
    }

    private void verifyResponses(
            StepVerifier.Step<SubscribeResponse> stepVerifier, List<ConsensusTopicResponse> stubbedResponses) {
        stubbedResponses.forEach(
                response -> stepVerifier.thenAwait(TOPIC_RESPONSE_DELAY).expectNextCount(1L));
        stepVerifier.expectComplete().verify(WAIT);
    }

    private ConsensusTopicResponse response(Long sequenceNumber) {
        return ConsensusTopicResponse.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(START_TIME.plusSeconds(sequenceNumber).getEpochSecond())
                        .setNanos(START_TIME.getNano())
                        .build())
                .setSequenceNumber(sequenceNumber)
                .setMessage(ByteString.copyFrom(Utility.generateMessage(256)))
                .build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    @Data
    public class ConsensusServiceStub extends ConsensusServiceGrpc.ConsensusServiceImplBase {

        private Flux<ConsensusTopicResponse> responses = Flux.empty();
        private ConsensusTopicQuery.Builder request = ConsensusTopicQuery.newBuilder()
                .setConsensusEndTime(toTimestamp(properties.getEndTime()))
                .setConsensusStartTime(toTimestamp(properties.getStartTime()))
                .setLimit(properties.getLimit())
                .setTopicID(TopicID.newBuilder().setTopicNum(1000).build());

        @Override
        public void subscribeTopic(
                ConsensusTopicQuery consensusTopicQuery, StreamObserver<ConsensusTopicResponse> streamObserver) {
            log.debug("subscribeTopic: {}", consensusTopicQuery);
            assertThat(consensusTopicQuery).isEqualTo(request.build());
            responses
                    .delayElements(TOPIC_RESPONSE_DELAY)
                    .doOnComplete(streamObserver::onCompleted)
                    .doOnError(streamObserver::onError)
                    .doOnNext(streamObserver::onNext)
                    .doOnNext(t -> log.trace("Next: {}", t))
                    .subscribe();
        }
    }
}
