// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.transaction.FixedCustomFee;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.repository.CustomFeeRepository;
import org.hiero.mirror.restjava.repository.TopicRepository;
import org.hiero.mirror.restjava.service.fee.FeeEstimationContext.CacheEntityType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;

@Named
@RequiredArgsConstructor
final class FeeTopicStore implements ReadableTopicStore {

    private static final Object MARKER = new Object();

    private final TopicRepository topicRepository;
    private final CustomFeeRepository customFeeRepository;

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public Topic getTopic(@NonNull final TopicID id) {
        if (!hasBeenRead(id)) {
            markRead(id, load(id));
        }
        final var value = getReadCache().get(id);
        return value == MARKER ? null : (Topic) value;
    }

    @Override
    public long sizeOfState() {
        return 0;
    }

    private Map<Object, Object> getReadCache() {
        return FeeEstimationContext.get().getReadCache(CacheEntityType.TOPIC);
    }

    private boolean hasBeenRead(final TopicID id) {
        return getReadCache().containsKey(id);
    }

    private void markRead(final TopicID id, @Nullable final Topic value) {
        if (FeeEstimationContext.get().readCount() >= FeeEstimationContext.MAX_LOOKUPS) {
            throw new IllegalArgumentException("Fee estimation exceeded the maximum of %d entity lookups"
                    .formatted(FeeEstimationContext.MAX_LOOKUPS));
        }
        getReadCache().put(id, value == null ? MARKER : value);
    }

    @Nullable
    private Topic load(final TopicID id) {
        return topicRepository
                .findById(id.topicNum())
                .map(topic -> toTopic(id, topic, customFeeRepository))
                .orElse(null);
    }

    private static Topic toTopic(
            final TopicID id,
            final org.hiero.mirror.common.domain.topic.Topic topic,
            final CustomFeeRepository customFeeRepository) {
        return Topic.newBuilder()
                .topicId(id)
                .customFees(getCustomFees(topic.getId(), customFeeRepository))
                .build();
    }

    // Calculator only checks isEmpty(); FixedCustomFee.DEFAULT is a safe placeholder.
    private static List<FixedCustomFee> getCustomFees(
            final long topicId, final CustomFeeRepository customFeeRepository) {
        return customFeeRepository
                .findById(topicId)
                .filter(customFee -> !CollectionUtils.isEmpty(customFee.getFixedFees()))
                .map(customFee -> Collections.nCopies(customFee.getFixedFees().size(), FixedCustomFee.DEFAULT))
                .orElseGet(List::of);
    }
}
