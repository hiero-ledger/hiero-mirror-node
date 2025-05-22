// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import reactor.core.publisher.Flux;

/**
 * Listens for new topic messages, filtering them as appropriate for the current subscriber. Implementations can be
 * either hot or cold publishers.
 */
public interface TopicListener {

    String METRIC = "hiero_mirror_grpc_listener";
    String METRIC_TAG = "mode";

    Flux<TopicMessage> listen(TopicMessageFilter filter);
}
