// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
public abstract class SharedTopicListener implements TopicListener {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ListenerProperties listenerProperties;

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        return getSharedListener(filter)
                .doOnSubscribe(s -> log.info("Subscribing: {}", filter))
                .onBackpressureBuffer(listenerProperties.getMaxBufferSize(), BufferOverflowStrategy.ERROR)
                .publishOn(Schedulers.boundedElastic(), false, listenerProperties.getPrefetch());
    }

    protected abstract Flux<TopicMessage> getSharedListener(TopicMessageFilter filter);
}
