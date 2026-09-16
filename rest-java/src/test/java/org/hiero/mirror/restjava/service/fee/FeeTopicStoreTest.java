// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.TopicID;
import java.util.Optional;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.restjava.repository.CustomFeeRepository;
import org.hiero.mirror.restjava.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({FeeEstimationContextExtension.class, MockitoExtension.class})
class FeeTopicStoreTest {

    private static final long TOPIC_NUM = 123L;
    private static final TopicID TOPIC_ID =
            TopicID.newBuilder().topicNum(TOPIC_NUM).build();

    @InjectMocks
    private FeeTopicStore store;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private CustomFeeRepository customFeeRepository;

    private DomainBuilder domainBuilder;

    @BeforeEach
    void setup() {
        domainBuilder = new DomainBuilder();
    }

    @Test
    void getTopicReturnsNullWhenNotFound() {
        when(topicRepository.findById(TOPIC_NUM)).thenReturn(Optional.empty());

        assertThat(store.getTopic(TOPIC_ID)).isNull();
    }

    @Test
    void getTopicWithoutCustomFees() {
        var topic = domainBuilder.topic().customize(t -> t.id(TOPIC_NUM)).get();
        when(topicRepository.findById(TOPIC_NUM)).thenReturn(Optional.of(topic));
        when(customFeeRepository.findById(TOPIC_NUM)).thenReturn(Optional.empty());

        var result = store.getTopic(TOPIC_ID);

        assertThat(result).isNotNull();
        assertThat(result.topicId()).isEqualTo(TOPIC_ID);
        assertThat(result.customFees()).isEmpty();
    }

    @Test
    void getTopicWithCustomFees() {
        var topic = domainBuilder.topic().customize(t -> t.id(TOPIC_NUM)).get();
        var customFee = domainBuilder
                .customFee()
                .customize(cf -> cf.entityId(TOPIC_NUM))
                .get();
        when(topicRepository.findById(TOPIC_NUM)).thenReturn(Optional.of(topic));
        when(customFeeRepository.findById(TOPIC_NUM)).thenReturn(Optional.of(customFee));

        var result = store.getTopic(TOPIC_ID);

        assertThat(result).isNotNull();
        assertThat(result.topicId()).isEqualTo(TOPIC_ID);
        assertThat(result.customFees()).hasSize(customFee.getFixedFees().size());
    }

    @Test
    void sizeOfStateReturnsZero() {
        assertThat(store.sizeOfState()).isZero();
    }

    @Test
    void getTopicMemoizesRepeatedLookups() {
        when(topicRepository.findById(TOPIC_NUM)).thenReturn(Optional.empty());

        assertThat(store.getTopic(TOPIC_ID)).isNull();
        assertThat(store.getTopic(TOPIC_ID)).isNull();

        verify(topicRepository, times(1)).findById(TOPIC_NUM);
    }

    @Test
    void rejectsWhenLookupCapExhausted() {
        when(topicRepository.findById(anyLong())).thenReturn(Optional.empty());

        for (int i = 1; i <= FeeEstimationContext.MAX_LOOKUPS; i++) {
            store.getTopic(TopicID.newBuilder().topicNum(i).build());
        }

        assertThatThrownBy(() -> store.getTopic(TopicID.newBuilder()
                        .topicNum(FeeEstimationContext.MAX_LOOKUPS + 1L)
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum of %d entity lookups".formatted(FeeEstimationContext.MAX_LOOKUPS));
    }
}
