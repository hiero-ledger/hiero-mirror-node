// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.grpc.listener;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import reactor.core.publisher.Flux;

/**
 * Listens for new topic messages, filtering them as appropriate for the current subscriber. Implementations can be
 * either hot or cold publishers.
 */
public interface TopicListener {

    String METRIC = "hedera_mirror_grpc_listener";
    String METRIC_TAG = "mode";

    Flux<TopicMessage> listen(TopicMessageFilter filter);
}
