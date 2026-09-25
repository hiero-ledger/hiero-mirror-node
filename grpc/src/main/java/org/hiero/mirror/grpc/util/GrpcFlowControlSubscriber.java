// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.util;

import io.grpc.stub.ServerCallStreamObserver;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;

public final class GrpcFlowControlSubscriber<T> extends BaseSubscriber<T> {

    private final ServerCallStreamObserver<T> responseObserver;

    public GrpcFlowControlSubscriber(ServerCallStreamObserver<T> responseObserver) {
        this.responseObserver = responseObserver;
        responseObserver.setOnCancelHandler(this::cancel);
        responseObserver.setOnReadyHandler(this::requestIfReady);
    }

    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        requestIfReady();
    }

    @Override
    protected void hookOnNext(T value) {
        responseObserver.onNext(value);
        requestIfReady();
    }

    @Override
    protected void hookOnComplete() {
        responseObserver.onCompleted();
    }

    @Override
    protected void hookOnError(Throwable throwable) {
        responseObserver.onError(throwable);
    }

    // Ties Reactor demand to transport readiness so a stalled client can't force unbounded buffering.
    private void requestIfReady() {
        if (responseObserver.isReady()) {
            request(1);
        }
    }
}
