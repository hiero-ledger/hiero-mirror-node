// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.grpc.retriever;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import reactor.core.publisher.Flux;

/**
 * Retrieves historical topic messages. This is a cold publisher retrieving only when subscribed and completing once all
 * current results in the database are returned.
 */
public interface TopicMessageRetriever {

    String METRIC = "hedera_mirror_grpc_retriever";

    Flux<TopicMessage> retrieve(TopicMessageFilter filter, boolean throttled);
}
