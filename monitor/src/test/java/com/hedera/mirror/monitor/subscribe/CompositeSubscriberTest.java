// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import org.hiero.mirror.monitor.publish.PublishResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CompositeSubscriberTest {

    private static final Duration WAIT = Duration.ofSeconds(10L);

    @Mock
    private MirrorSubscriber mirrorSubscriber1;

    @Mock
    private MirrorSubscriber mirrorSubscriber2;

    private CompositeSubscriber compositeSubscriber;

    @BeforeEach
    void setup() {
        compositeSubscriber = new CompositeSubscriber(Arrays.asList(mirrorSubscriber1, mirrorSubscriber2));
    }

    @Test
    void onPublish() {
        PublishResponse publishResponse = PublishResponse.builder().build();
        compositeSubscriber.onPublish(publishResponse);
        verify(mirrorSubscriber1).onPublish(publishResponse);
        verify(mirrorSubscriber2).onPublish(publishResponse);
    }

    @Test
    void subscribe() {
        SubscribeResponse subscribeResponse1 = SubscribeResponse.builder().build();
        SubscribeResponse subscribeResponse2 = SubscribeResponse.builder().build();
        when(mirrorSubscriber1.subscribe()).thenReturn(Flux.just(subscribeResponse1));
        when(mirrorSubscriber2.subscribe()).thenReturn(Flux.just(subscribeResponse2));

        StepVerifier.withVirtualTime(() -> compositeSubscriber.subscribe())
                .thenAwait(WAIT)
                .expectNext(subscribeResponse1, subscribeResponse2)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void subscriptions() {
        TestScenario subscription1 = new TestScenario();
        TestScenario subscription2 = new TestScenario();
        when(mirrorSubscriber1.getSubscriptions()).thenReturn(Flux.just(subscription1));
        when(mirrorSubscriber2.getSubscriptions()).thenReturn(Flux.just(subscription2));

        StepVerifier.withVirtualTime(() -> compositeSubscriber.getSubscriptions())
                .thenAwait(WAIT)
                .expectNext(subscription1, subscription2)
                .expectComplete()
                .verify(WAIT);
    }
}
