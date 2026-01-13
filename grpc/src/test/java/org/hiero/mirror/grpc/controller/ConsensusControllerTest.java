// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static org.hiero.mirror.grpc.domain.ReactiveDomainBuilder.TOPIC_ID;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.api.proto.ReactorConsensusServiceGrpc;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import lombok.CustomLog;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.hiero.mirror.grpc.domain.ReactiveDomainBuilder;
import org.hiero.mirror.grpc.listener.ListenerProperties;
import org.hiero.mirror.grpc.util.ProtoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@CustomLog
@ExtendWith(OutputCaptureExtension.class)
class ConsensusControllerTest extends GrpcIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10L);
    private final long future = DomainUtils.convertToNanosMax(Instant.now().plusSeconds(10L));

    @Autowired
    private ReactorConsensusServiceGrpc.ReactorConsensusServiceStub grpcConsensusService;

    @Autowired
    private ConsensusServiceGrpc.ConsensusServiceBlockingStub blockingService;

    @Autowired
    private ReactiveDomainBuilder domainBuilder;

    @Resource
    private ListenerProperties listenerProperties;

    @BeforeEach
    void setup() {
        listenerProperties.setEnabled(true);
        domainBuilder.entity().block();
    }

    @AfterEach
    void after() {
        listenerProperties.setEnabled(false);
    }

    @Test
    void missingTopicID() {
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder().build();
        StepVerifier.withVirtualTime(() -> grpcConsensusService.subscribeTopic(Mono.just(query)))
                .thenAwait(WAIT)
                .expectErrorSatisfies(
                        t -> assertException(t, Status.Code.INVALID_ARGUMENT, "topicId: must not be " + "null"))
                .verify(WAIT);
    }

    @Test
    void invalidTopicID() {
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setTopicID(TopicID.newBuilder().setTopicNum(-1).build())
                .build();
        StepVerifier.withVirtualTime(() -> grpcConsensusService.subscribeTopic(Mono.just(query)))
                .thenAwait(WAIT)
                .expectErrorSatisfies(t -> assertException(t, Status.Code.INVALID_ARGUMENT, "Invalid entity ID"))
                .verify(WAIT);
    }

    @Test
    void maxConsensusEndTime(CapturedOutput capturedOutput) {
        var topicMessage1 = domainBuilder.topicMessage().block();
        var query = ConsensusTopicQuery.newBuilder()
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())
                .setConsensusEndTime(
                        Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).build())
                .setTopicID(TOPIC_ID.toTopicID())
                .setLimit(1L)
                .build();
        assertThat(blockingService.subscribeTopic(query))
                .toIterable()
                .hasSize(1)
                .containsSequence(grpcResponse(topicMessage1));
        assertThat(capturedOutput.getAll()).doesNotContain("Long overflow when converting time");
    }

    @Test
    void maxConsensusStartTime(CapturedOutput capturedOutput) {
        var query = ConsensusTopicQuery.newBuilder()
                .setConsensusStartTime(
                        Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).build())
                .setTopicID(TOPIC_ID.toTopicID())
                .setLimit(1L)
                .build();
        blockingService.subscribeTopic(query);
        assertThat(capturedOutput.getAll()).doesNotContain("Long overflow when converting time");
    }

    @Test
    void constraintViolationException() {
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setTopicID(TopicID.newBuilder().build())
                .setLimit(-1)
                .build();

        StepVerifier.withVirtualTime(() -> grpcConsensusService.subscribeTopic(Mono.just(query)))
                .thenAwait(WAIT)
                .expectErrorSatisfies(t ->
                        assertException(t, Status.Code.INVALID_ARGUMENT, "limit: must be greater than or equal to 0"))
                .verify(WAIT);
    }

    @Test
    void subscribeTopicReactive() {
        var topicMessage1 = domainBuilder.topicMessage().block();
        var topicMessage2 = domainBuilder.topicMessage().block();
        var topicMessage3 = domainBuilder.topicMessage().block();

        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setLimit(5L)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())
                .setTopicID(TOPIC_ID.toTopicID())
                .build();

        var generator = domainBuilder.topicMessages(2, future);

        StepVerifier.withVirtualTime(() -> grpcConsensusService.subscribeTopic(Mono.just(query)))
                .thenAwait(WAIT)
                .expectNext(grpcResponse(topicMessage1))
                .expectNext(grpcResponse(topicMessage2))
                .expectNext(grpcResponse(topicMessage3))
                .then(generator::blockLast)
                .expectNextCount(2)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void subscribeTopicBlocking() {
        var topicMessage1 = domainBuilder.topicMessage().block();
        var topicMessage2 = domainBuilder.topicMessage().block();
        var topicMessage3 = domainBuilder.topicMessage().block();

        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setLimit(3L)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())
                .setTopicID(TOPIC_ID.toTopicID())
                .build();

        assertThat(blockingService.subscribeTopic(query))
                .toIterable()
                .hasSize(3)
                .containsSequence(
                        grpcResponse(topicMessage1), grpcResponse(topicMessage2), grpcResponse(topicMessage3));
    }

    @Test
    void subscribeTopicQueryLongOverflowEndTime() {
        var topicMessage1 = domainBuilder.topicMessage().block();
        var topicMessage2 = domainBuilder.topicMessage().block();
        var topicMessage3 = domainBuilder.topicMessage().block();

        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setLimit(5L)
                .setConsensusStartTime(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setConsensusEndTime(Timestamp.newBuilder()
                        .setSeconds(31556889864403199L)
                        .setNanos(999999999)
                        .build())
                .setTopicID(TOPIC_ID.toTopicID())
                .build();

        var generator = domainBuilder.topicMessages(2, future);

        StepVerifier.withVirtualTime(() -> grpcConsensusService.subscribeTopic(Mono.just(query)))
                .thenAwait(WAIT)
                .expectNext(grpcResponse(topicMessage1))
                .expectNext(grpcResponse(topicMessage2))
                .expectNext(grpcResponse(topicMessage3))
                .then(generator::blockLast)
                .expectNextCount(2)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void subscribeVerifySequence() {
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setLimit(7L)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())
                .setTopicID(TOPIC_ID.toTopicID())
                .build();

        Flux<TopicMessage> generator = domainBuilder.topicMessages(4, future);

        StepVerifier.withVirtualTime(() -> grpcConsensusService
                        .subscribeTopic(Mono.just(query))
                        .map(ConsensusTopicResponse::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L, 3L)
                .thenAwait(Duration.ofMillis(50))
                .then(generator::blockLast)
                .expectNext(4L, 5L, 6L, 7L)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void fragmentedMessagesGroupAcrossHistoricAndIncoming() {
        var now = DomainUtils.now();
        var payerAccountId = domainBuilder.entityId();
        domainBuilder.topicMessage(t -> t.sequenceNumber(1)).block();
        domainBuilder
                .topicMessage(t -> t.sequenceNumber(2)
                        .chunkNum(1)
                        .chunkTotal(2)
                        .validStartTimestamp(now)
                        .payerAccountId(payerAccountId)
                        .consensusTimestamp(now + 1))
                .block();
        domainBuilder
                .topicMessage(t -> t.sequenceNumber(3)
                        .chunkNum(2)
                        .chunkTotal(2)
                        .validStartTimestamp(now + 1)
                        .payerAccountId(payerAccountId)
                        .consensusTimestamp(now + 2))
                .block();
        domainBuilder
                .topicMessage(t -> t.sequenceNumber(4).consensusTimestamp(now + 3))
                .block();
        domainBuilder
                .topicMessage(t -> t.sequenceNumber(5)
                        .chunkNum(1)
                        .chunkTotal(3)
                        .validStartTimestamp(now + 3)
                        .payerAccountId(payerAccountId)
                        .consensusTimestamp(now + 4))
                .block();

        // fragment message split across historic and incoming
        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.sequenceNumber(6)
                        .chunkNum(2)
                        .chunkTotal(3)
                        .validStartTimestamp(now + 4)
                        .payerAccountId(payerAccountId)
                        .consensusTimestamp(now + 5 * NANOS_PER_SECOND)
                        .initialTransactionId(null)),
                domainBuilder.topicMessage(t -> t.sequenceNumber(7)
                        .chunkNum(3)
                        .chunkTotal(3)
                        .validStartTimestamp(now + 5)
                        .payerAccountId(payerAccountId)
                        .consensusTimestamp(now + 6 * NANOS_PER_SECOND)
                        .initialTransactionId(new byte[] {1, 2})),
                domainBuilder.topicMessage(t -> t.sequenceNumber(8).consensusTimestamp(now + 7 * NANOS_PER_SECOND)));

        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())
                .setTopicID(TOPIC_ID.toTopicID())
                .build();

        StepVerifier.withVirtualTime(() -> grpcConsensusService
                        .subscribeTopic(Mono.just(query))
                        // mapper doesn't handle null values so replace with 0's
                        .map(x -> x.hasChunkInfo() ? x.getChunkInfo().getNumber() : 0))
                .thenAwait(WAIT)
                .expectNext(1, 1, 2, 1, 1)
                .then(generator::blockLast)
                .expectNext(2, 3, 1) // incoming messages
                .thenCancel()
                .verify(WAIT);
    }

    @Test
    void nullRunningHashVersion() {
        var topicMessage =
                domainBuilder.topicMessage(t -> t.runningHashVersion(null)).block();
        var query = ConsensusTopicQuery.newBuilder()
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())
                .setConsensusEndTime(
                        Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).build())
                .setTopicID(TOPIC_ID.toTopicID())
                .setLimit(1L)
                .build();
        assertThat(blockingService.subscribeTopic(query))
                .toIterable()
                .hasSize(1)
                .containsSequence(grpcResponse(topicMessage))
                .allSatisfy(t -> assertThat(t.getRunningHashVersion())
                        .isEqualTo(ConsensusController.DEFAULT_RUNNING_HASH_VERSION));
    }

    void assertException(Throwable t, Status.Code status, String message) {
        assertThat(t).isNotNull().isInstanceOf(StatusRuntimeException.class).hasMessageContaining(message);

        StatusRuntimeException statusRuntimeException = (StatusRuntimeException) t;
        assertThat(statusRuntimeException.getStatus().getCode()).isEqualTo(status);
    }

    @SneakyThrows
    private ConsensusTopicResponse grpcResponse(TopicMessage t) {
        var runningHashVersion = t.getRunningHashVersion() == null
                ? ConsensusController.DEFAULT_RUNNING_HASH_VERSION
                : t.getRunningHashVersion();
        return ConsensusTopicResponse.newBuilder()
                .setConsensusTimestamp(ProtoUtil.toTimestamp(t.getConsensusTimestamp()))
                .setMessage(ProtoUtil.toByteString(t.getMessage()))
                .setRunningHash(ProtoUtil.toByteString(t.getRunningHash()))
                .setRunningHashVersion(runningHashVersion)
                .setSequenceNumber(t.getSequenceNumber())
                .setChunkInfo(ConsensusMessageChunkInfo.newBuilder()
                        .setNumber(t.getChunkNum())
                        .setTotal(t.getChunkTotal())
                        .setInitialTransactionID(TransactionID.parseFrom(t.getInitialTransactionId())))
                .build();
    }
}
