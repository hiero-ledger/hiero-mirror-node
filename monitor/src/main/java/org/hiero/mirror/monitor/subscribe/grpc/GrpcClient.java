// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.grpc;

import org.hiero.mirror.monitor.subscribe.SubscribeResponse;
import reactor.core.publisher.Flux;

public interface GrpcClient extends AutoCloseable {

    void close();

    Flux<SubscribeResponse> subscribe(GrpcSubscription subscription);
}
