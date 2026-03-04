// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static org.hiero.mirror.grpc.domain.ReactiveDomainBuilder.TOPIC_ID;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
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
import org.springframework.grpc.client.ImportGrpcClients;

@CustomLog
@ExtendWith(OutputCaptureExtension.class)
@ImportGrpcClients(types = {ConsensusServiceGrpc.ConsensusServiceBlockingStub.class})
@RequiredArgsConstructor
class ConsensusControllerTest extends GrpcIntegrationTest {

    private final long future = DomainUtils.convertToNanosMax(Instant.now().plusSeconds(10L));

    private final ConsensusServiceGrpc.ConsensusServiceBlockingStub blockingService;

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
        final var query = ConsensusTopicQuery.newBuilder().build();

        assertThatThrownBy(() -> {
                    final var iterator = blockingService.subscribeTopic(query);
                    iterator.hasNext();
                })
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void invalidTopicID() {
        final var query = ConsensusTopicQuery.newBuilder()
                .setTopicID(TopicID.newBuilder().setTopicNum(-1).build())
                .build();
        assertThatThrownBy(() -> {
                    final var iterator = blockingService.subscribeTopic(query);
                    iterator.hasNext();
                })
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("Invalid entity ID")
                .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
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

        assertThatThrownBy(() -> {
                    final var iterator = blockingService.subscribeTopic(query);
                    iterator.hasNext();
                })
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("limit: must be greater than or equal to 0")
                .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
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

        domainBuilder.topicMessages(2, future).blockLast();

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

        assertThat(blockingService.subscribeTopic(query))
                .toIterable()
                .hasSize(5)
                .startsWith(grpcResponse(topicMessage1), grpcResponse(topicMessage2), grpcResponse(topicMessage3));
    }

    @Test
    void subscribeVerifySequence() {
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        domainBuilder.topicMessages(4, future).blockLast();

        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setLimit(7L)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())
                .setTopicID(TOPIC_ID.toTopicID())
                .build();

        var responseIterator = blockingService.subscribeTopic(query);
        List<Long> sequences = new ArrayList<>();

        responseIterator.forEachRemaining(r -> sequences.add(r.getSequenceNumber()));

        assertThat(sequences).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
    }

    @Test
    void fragmentedMessagesGroupAcrossHistoricAndIncoming() {
        final var now = DomainUtils.now();
        final var payerAccountId = domainBuilder.entityId();

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

        domainBuilder
                .topicMessage(t -> t.sequenceNumber(6)
                        .chunkNum(2)
                        .chunkTotal(3)
                        .validStartTimestamp(now + 4)
                        .payerAccountId(payerAccountId)
                        .consensusTimestamp(now + 5 * NANOS_PER_SECOND)
                        .initialTransactionId(null))
                .block();
        domainBuilder
                .topicMessage(t -> t.sequenceNumber(7)
                        .chunkNum(3)
                        .chunkTotal(3)
                        .validStartTimestamp(now + 5)
                        .payerAccountId(payerAccountId)
                        .consensusTimestamp(now + 6 * NANOS_PER_SECOND)
                        .initialTransactionId(new byte[] {1, 2}))
                .block();
        domainBuilder
                .topicMessage(t -> t.sequenceNumber(8).consensusTimestamp(now + 7 * NANOS_PER_SECOND))
                .block();

        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())
                .setTopicID(TOPIC_ID.toTopicID())
                .setLimit(8)
                .build();

        final var response = blockingService.subscribeTopic(query);
        List<Integer> chunkNumbers = new ArrayList<>();

        response.forEachRemaining(
                x -> chunkNumbers.add(x.hasChunkInfo() ? x.getChunkInfo().getNumber() : 0));

        assertThat(chunkNumbers).containsExactly(1, 1, 2, 1, 1, 2, 3, 1);
    }

    @Test
    void nullRunningHashVersion() {
        final var topicMessage =
                domainBuilder.topicMessage(t -> t.runningHashVersion(null)).block();
        final var query = ConsensusTopicQuery.newBuilder()
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
