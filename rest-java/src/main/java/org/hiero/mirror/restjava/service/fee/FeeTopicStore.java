// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.TopicID;
import jakarta.inject.Named;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.restjava.repository.CustomFeeRepository;
import org.hiero.mirror.restjava.repository.TopicRepository;

/**
 * Live topic + custom fee lookup from mirror DB for fee estimation (STATE mode).
 */
@Named
public final class FeeTopicStore {

    private final TopicRepository topicRepository;
    private final CustomFeeRepository customFeeRepository;

    public FeeTopicStore(TopicRepository topicRepository, CustomFeeRepository customFeeRepository) {
        this.topicRepository = topicRepository;
        this.customFeeRepository = customFeeRepository;
    }

    /**
     * Returns topic fee view for the given id, or {@code null} if the topic does not exist.
     */
    public TopicFeeView getTopic(TopicID topicId) {
        long num = topicId.topicNum();
        if (topicRepository.findById(num).isEmpty()) {
            return null;
        }
        List<com.hedera.hapi.node.transaction.CustomFee> fees = List.of();
        Optional<org.hiero.mirror.common.domain.token.CustomFee> cfOpt = customFeeRepository.findById(num);
        if (cfOpt.isPresent()) {
            fees = DomainCustomFeeMapping.toPbjTopicCustomFees(cfOpt.get());
        }
        return new TopicFeeView(topicId, fees);
    }

    /** Hook for Hedera throttle/state sizing; mirror-backed store does not contribute in-process state size. */
    public long sizeOfState() {
        return 0L;
    }
}
