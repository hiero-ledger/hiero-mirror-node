// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.hiero.mirror.grpc.domain.ReactiveDomainBuilder;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TopicMessageRepositoryTest extends GrpcIntegrationTest {

    private final TopicMessageRepository topicMessageRepository;
    private final ReactiveDomainBuilder domainBuilder;

    @Test
    void findByFilterEmpty() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(0)
                .topicId(EntityId.of(100L))
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).isEmpty();
    }

    @Test
    void findByFilterNoMatch() {
        TopicMessage topicMessage = domainBuilder.topicMessage().block();
        // second topic message
        domainBuilder.topicMessage().block();
        // third topic message
        domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(DomainUtils.convertToNanosMax(Instant.now().plusSeconds(10)))
                .topicId(topicMessage.getTopicId())
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).isEmpty();
    }

    @Test
    void findByFilterWithTopicId() {
        TopicMessage topicMessage1 =
                domainBuilder.topicMessage(t -> t.topicId(EntityId.of(-1))).block();
        TopicMessage topicMessage2 =
                domainBuilder.topicMessage(t -> t.topicId(EntityId.of(-2))).block();
        // third topic message
        domainBuilder.topicMessage(t -> t.topicId(EntityId.of(-3))).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicId(EntityId.of(-2L))
                .startTime(topicMessage1.getConsensusTimestamp())
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).containsExactly(topicMessage2);
    }

    @Test
    void findByFilterWithStartTime() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(topicMessage2.getConsensusTimestamp())
                .topicId(topicMessage1.getTopicId())
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).containsExactly(topicMessage2, topicMessage3);
    }

    @Test
    void findByFilterWithEndTime() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(topicMessage1.getConsensusTimestamp())
                .endTime(topicMessage3.getConsensusTimestamp())
                .topicId(topicMessage1.getTopicId())
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).containsExactly(topicMessage1, topicMessage2);
    }

    @Test
    void findByFilterWithLimit() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        // second topic message
        domainBuilder.topicMessage().block();
        // third topic message
        domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .limit(1)
                .startTime(topicMessage1.getConsensusTimestamp())
                .topicId(topicMessage1.getTopicId())
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).containsExactly(topicMessage1);
    }

    @Test
    void findLatest() {
        // given
        var topicMessage1 = domainBuilder.topicMessage().block();
        var topicMessage2 = domainBuilder.topicMessage().block();
        domainBuilder
                .transaction(t -> t.type(TransactionType.CRYPTOTRANSFER.getProtoId()))
                .block();
        domainBuilder.transaction(t -> t.result(10)).block();

        // when, then
        assertThat(topicMessageRepository.findLatest(topicMessage1.getConsensusTimestamp() - 1, 10))
                .containsExactly(topicMessage1, topicMessage2);
        assertThat(topicMessageRepository.findLatest(topicMessage1.getConsensusTimestamp() - 1, 1))
                .containsExactly(topicMessage1);
        assertThat(topicMessageRepository.findLatest(topicMessage1.getConsensusTimestamp(), 10))
                .containsExactly(topicMessage2);
        assertThat(topicMessageRepository.findLatest(topicMessage2.getConsensusTimestamp(), 10))
                .isEmpty();
    }
}
