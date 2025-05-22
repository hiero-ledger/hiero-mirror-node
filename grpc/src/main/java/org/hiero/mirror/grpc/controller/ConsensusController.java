// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.controller;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.api.proto.ReactorConsensusServiceGrpc;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Objects;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.hiero.mirror.grpc.service.TopicMessageService;
import org.hiero.mirror.grpc.util.ProtoUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * GRPC calls their protocol adapter layer a service, but most of the industry calls this layer the controller layer.
 * See the Front Controller pattern or Model-View-Controller (MVC) pattern. The service layer is generally reserved for
 * non-protocol specific business logic so to avoid confusion with our TopicMessageService we'll name this GRPC layer as
 * controller.
 */
@GrpcService
@CustomLog
@RequiredArgsConstructor
public class ConsensusController extends ReactorConsensusServiceGrpc.ConsensusServiceImplBase {

    // Blockstreams no longer contain runningHashVersion, default to the latest version
    static final int DEFAULT_RUNNING_HASH_VERSION = 3;

    private final TopicMessageService topicMessageService;

    @Override
    public Flux<ConsensusTopicResponse> subscribeTopic(Mono<ConsensusTopicQuery> request) {
        return request.map(this::toFilter)
                .flatMapMany(topicMessageService::subscribeTopic)
                .map(this::toResponse)
                .onErrorMap(ProtoUtil::toStatusRuntimeException);
    }

    private TopicMessageFilter toFilter(ConsensusTopicQuery query) {
        var filter = TopicMessageFilter.builder().limit(query.getLimit());

        if (query.hasTopicID()) {
            filter.topicId(EntityId.of(query.getTopicID()));
        }

        if (query.hasConsensusStartTime()) {
            long startTime = convertTimestamp(query.getConsensusStartTime());
            filter.startTime(startTime);
        }

        if (query.hasConsensusEndTime()) {
            long endTime = convertTimestamp(query.getConsensusEndTime());
            filter.endTime(endTime);
        }

        return filter.build();
    }

    // The util class logs an error if the timestamp overflows, so return MAX_VALUE if it's close to max db bigint.
    private long convertTimestamp(Timestamp timestamp) {
        if (timestamp.getSeconds() >= 9223372035L) {
            return Long.MAX_VALUE;
        }
        return DomainUtils.timestampInNanosMax(timestamp);
    }

    // Consider caching this conversion for multiple subscribers to the same topic if the need arises.
    private ConsensusTopicResponse toResponse(TopicMessage t) {
        var consensusTopicResponseBuilder = ConsensusTopicResponse.newBuilder()
                .setConsensusTimestamp(ProtoUtil.toTimestamp(t.getConsensusTimestamp()))
                .setMessage(ProtoUtil.toByteString(t.getMessage()))
                .setRunningHash(ProtoUtil.toByteString(t.getRunningHash()))
                .setRunningHashVersion(
                        Objects.requireNonNullElse(t.getRunningHashVersion(), DEFAULT_RUNNING_HASH_VERSION))
                .setSequenceNumber(t.getSequenceNumber());

        if (t.getChunkNum() != null) {
            ConsensusMessageChunkInfo.Builder chunkBuilder = ConsensusMessageChunkInfo.newBuilder()
                    .setNumber(t.getChunkNum())
                    .setTotal(t.getChunkTotal());

            TransactionID transactionID = parseTransactionID(
                    t.getInitialTransactionId(), t.getTopicId().getNum(), t.getSequenceNumber());
            EntityId payerAccountEntity = t.getPayerAccountId();
            var validStartInstant = ProtoUtil.toTimestamp(t.getValidStartTimestamp());

            if (transactionID != null) {
                chunkBuilder.setInitialTransactionID(transactionID);
            } else if (payerAccountEntity != null && validStartInstant != null) {
                chunkBuilder.setInitialTransactionID(TransactionID.newBuilder()
                        .setAccountID(payerAccountEntity.toAccountID())
                        .setTransactionValidStart(validStartInstant)
                        .build());
            }

            consensusTopicResponseBuilder.setChunkInfo(chunkBuilder.build());
        }

        return consensusTopicResponseBuilder.build();
    }

    private TransactionID parseTransactionID(byte[] transactionIdBytes, long topicId, long sequenceNumber) {
        if (transactionIdBytes == null) {
            return null;
        }
        try {
            return TransactionID.parseFrom(transactionIdBytes);
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse TransactionID for topic {} sequence number {}", topicId, sequenceNumber);
            return null;
        }
    }
}
