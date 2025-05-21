// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.retriever;

import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import reactor.core.publisher.Flux;

/**
 * Retrieves historical topic messages. This is a cold publisher retrieving only when subscribed and completing once all
 * current results in the database are returned.
 */
public interface TopicMessageRetriever {

    String METRIC = "hiero_mirror_grpc_retriever";

    Flux<TopicMessage> retrieve(TopicMessageFilter filter, boolean throttled);
}
