// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.subscribe.grpc;

import com.hedera.mirror.monitor.subscribe.SubscribeResponse;
import reactor.core.publisher.Flux;

public interface GrpcClient extends AutoCloseable {

    void close();

    Flux<SubscribeResponse> subscribe(GrpcSubscription subscription);
}
