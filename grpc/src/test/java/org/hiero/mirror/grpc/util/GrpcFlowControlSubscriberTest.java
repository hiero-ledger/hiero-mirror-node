// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.stub.ServerCallStreamObserver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Subscription;

final class GrpcFlowControlSubscriberTest {

    @SuppressWarnings("unchecked")
    private final ServerCallStreamObserver<String> responseObserver = mock(ServerCallStreamObserver.class);

    @Test
    void requestsOneAtStartupWhileReady() {
        when(responseObserver.isReady()).thenReturn(true);
        final var subscription = new RecordingSubscription();

        new GrpcFlowControlSubscriber<>(responseObserver).onSubscribe(subscription);

        assertThat(subscription.requests).containsExactly(1L);
    }

    @Test
    void doesNotRequestAtStartupWhenNotReady() {
        when(responseObserver.isReady()).thenReturn(false);
        final var subscription = new RecordingSubscription();

        new GrpcFlowControlSubscriber<>(responseObserver).onSubscribe(subscription);

        assertThat(subscription.requests).isEmpty();
    }

    @Test
    void requestsOneMoreAfterEachElementWhileReady() {
        when(responseObserver.isReady()).thenReturn(true);
        final var subscription = new RecordingSubscription();
        final var subscriber = new GrpcFlowControlSubscriber<>(responseObserver);

        subscriber.onSubscribe(subscription);
        subscriber.onNext("message");

        verify(responseObserver).onNext("message");
        assertThat(subscription.requests).containsExactly(1L, 1L);
    }

    @Test
    void stalledClientStopsDemandUntilTransportBecomesReadyAgain() {
        // Ready for the initial request, then not ready - mimics a client that stops reading.
        when(responseObserver.isReady()).thenReturn(true, false);
        final var subscription = new RecordingSubscription();
        final var subscriber = new GrpcFlowControlSubscriber<>(responseObserver);

        subscriber.onSubscribe(subscription);
        subscriber.onNext("message");

        assertThat(subscription.requests).containsExactly(1L);

        when(responseObserver.isReady()).thenReturn(true);
        onReadyHandler().run();

        assertThat(subscription.requests).containsExactly(1L, 1L);
    }

    @Test
    void cancelHandlerCancelsUpstreamSubscription() {
        when(responseObserver.isReady()).thenReturn(true);
        final var subscription = new RecordingSubscription();
        new GrpcFlowControlSubscriber<>(responseObserver).onSubscribe(subscription);

        onCancelHandler().run();

        assertThat(subscription.cancelled).isTrue();
    }

    @Test
    void forwardsCompletion() {
        when(responseObserver.isReady()).thenReturn(true);
        final var subscriber = new GrpcFlowControlSubscriber<>(responseObserver);
        subscriber.onSubscribe(new RecordingSubscription());

        subscriber.onComplete();

        verify(responseObserver).onCompleted();
    }

    @Test
    void forwardsErrorUnchanged() {
        when(responseObserver.isReady()).thenReturn(true);
        final var subscriber = new GrpcFlowControlSubscriber<>(responseObserver);
        subscriber.onSubscribe(new RecordingSubscription());
        final var throwable = new RuntimeException("boom");

        subscriber.onError(throwable);

        verify(responseObserver).onError(throwable);
    }

    private Runnable onReadyHandler() {
        final var captor = ArgumentCaptor.forClass(Runnable.class);
        verify(responseObserver).setOnReadyHandler(captor.capture());
        return captor.getValue();
    }

    private Runnable onCancelHandler() {
        final var captor = ArgumentCaptor.forClass(Runnable.class);
        verify(responseObserver).setOnCancelHandler(captor.capture());
        return captor.getValue();
    }

    private static final class RecordingSubscription implements Subscription {

        private final List<Long> requests = new ArrayList<>();
        private boolean cancelled;

        @Override
        public void request(long n) {
            requests.add(n);
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
