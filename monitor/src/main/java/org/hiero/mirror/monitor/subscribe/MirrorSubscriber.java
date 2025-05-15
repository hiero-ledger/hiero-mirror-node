// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe;

import org.hiero.mirror.monitor.publish.PublishResponse;
import reactor.core.publisher.Flux;

public interface MirrorSubscriber {

    default void onPublish(PublishResponse response) {}

    Flux<SubscribeResponse> subscribe();

    <T extends Scenario<?, ?>> Flux<T> getSubscriptions();
}
