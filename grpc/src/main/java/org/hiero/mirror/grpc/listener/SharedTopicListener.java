// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
public abstract class SharedTopicListener implements TopicListener {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ListenerProperties listenerProperties;

    @Override
    @SuppressWarnings("deprecation")
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        DirectProcessor<TopicMessage> overflowProcessor = DirectProcessor.create();
        FluxSink<TopicMessage> overflowSink = overflowProcessor.sink();

        // moving publishOn from after onBackpressureBuffer to after Flux.merge reduces CPU usage by up to 40%
        Flux<TopicMessage> topicMessageFlux = getSharedListener(filter)
                .doOnSubscribe(s -> log.info("Subscribing: {}", filter))
                .onBackpressureBuffer(
                        listenerProperties.getMaxBufferSize(), t -> overflowSink.error(Exceptions.failWithOverflow()))
                .doFinally(s -> overflowSink.complete());
        return Flux.merge(listenerProperties.getPrefetch(), topicMessageFlux, overflowProcessor)
                .publishOn(Schedulers.boundedElastic(), false, listenerProperties.getPrefetch());
    }

    protected abstract Flux<TopicMessage> getSharedListener(TopicMessageFilter filter);
}
