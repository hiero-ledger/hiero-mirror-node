// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.subscribe;

import com.hedera.mirror.monitor.publish.PublishResponse;
import jakarta.inject.Named;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

@Named
@Primary
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class CompositeSubscriber implements MirrorSubscriber {

    private final Collection<MirrorSubscriber> subscribers;

    @Override
    public void onPublish(PublishResponse response) {
        subscribers.forEach(s -> s.onPublish(response));
    }

    @Override
    public Flux<SubscribeResponse> subscribe() {
        return Flux.fromIterable(subscribers).flatMap(MirrorSubscriber::subscribe);
    }

    @Override
    public Flux<Scenario<?, ?>> getSubscriptions() {
        return Flux.fromIterable(subscribers).flatMap(MirrorSubscriber::getSubscriptions);
    }
}
