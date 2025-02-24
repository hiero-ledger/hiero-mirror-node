// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.subscribe;

import com.hedera.mirror.monitor.publish.PublishResponse;
import reactor.core.publisher.Flux;

public interface MirrorSubscriber {

    default void onPublish(PublishResponse response) {}

    Flux<SubscribeResponse> subscribe();

    <T extends Scenario<?, ?>> Flux<T> getSubscriptions();
}
