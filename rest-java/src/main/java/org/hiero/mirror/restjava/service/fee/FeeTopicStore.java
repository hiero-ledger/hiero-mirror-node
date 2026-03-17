// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.transaction.FixedCustomFee;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.repository.CustomFeeRepository;
import org.hiero.mirror.restjava.repository.TopicRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;

@Named
@RequiredArgsConstructor
public final class FeeTopicStore implements ReadableTopicStore {

    private final TopicRepository topicRepository;
    private final CustomFeeRepository customFeeRepository;

    @Override
    @Nullable
    public Topic getTopic(@NonNull final TopicID id) {
        return topicRepository
                .findById(id.topicNum())
                .map(topic -> toTopic(id, topic, customFeeRepository))
                .orElse(null);
    }

    @Override
    public long sizeOfState() {
        return 0;
    }

    private static Topic toTopic(
            final TopicID id,
            final org.hiero.mirror.common.domain.topic.Topic topic,
            final CustomFeeRepository customFeeRepository) {
        return Topic.newBuilder()
                .topicId(id)
                .adminKey(parseKey(topic.getAdminKey()))
                .submitKey(parseKey(topic.getSubmitKey()))
                .feeScheduleKey(parseKey(topic.getFeeScheduleKey()))
                .customFees(getCustomFees(topic.getId(), customFeeRepository))
                .build();
    }

    // Calculator only checks isEmpty(); FixedCustomFee.DEFAULT is a safe placeholder.
    private static List<FixedCustomFee> getCustomFees(long topicId, CustomFeeRepository customFeeRepository) {
        return customFeeRepository
                .findById(topicId)
                .filter(customFee -> !CollectionUtils.isEmpty(customFee.getFixedFees()))
                .map(customFee -> Collections.nCopies(customFee.getFixedFees().size(), FixedCustomFee.DEFAULT))
                .orElseGet(List::of);
    }

    @Nullable
    private static Key parseKey(final byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length == 0) {
            return null;
        }
        try {
            return Key.PROTOBUF.parse(Bytes.wrap(keyBytes));
        } catch (ParseException e) {
            return null;
        }
    }
}
