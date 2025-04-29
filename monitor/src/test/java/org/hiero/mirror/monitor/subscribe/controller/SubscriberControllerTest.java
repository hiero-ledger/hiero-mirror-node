// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.controller;

import static org.mockito.Mockito.when;

import java.util.Arrays;
import lombok.CustomLog;
import org.hiero.mirror.monitor.ScenarioProtocol;
import org.hiero.mirror.monitor.ScenarioStatus;
import org.hiero.mirror.monitor.subscribe.MirrorSubscriber;
import org.hiero.mirror.monitor.subscribe.TestScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@CustomLog
@ExtendWith(MockitoExtension.class)
class SubscriberControllerTest {

    @Mock
    private MirrorSubscriber mirrorSubscriber;

    private WebTestClient webTestClient;

    private TestScenario subscription1;
    private TestScenario subscription2;

    @BeforeEach
    void setup() {
        subscription1 = new TestScenario();
        subscription1.setName("grpc1");
        subscription1.setId(1);
        subscription1.setProtocol(ScenarioProtocol.GRPC);
        subscription1.setStatus(ScenarioStatus.COMPLETED);

        subscription2 = new TestScenario();
        subscription2.setName("rest1");
        subscription2.setId(1);
        subscription2.setProtocol(ScenarioProtocol.REST);
        subscription2.setStatus(ScenarioStatus.RUNNING);

        SubscriberController subscriberController = new SubscriberController(mirrorSubscriber);
        webTestClient = WebTestClient.bindToController(subscriberController).build();
        when(mirrorSubscriber.getSubscriptions()).thenReturn(Flux.just(subscription1, subscription2));
    }

    @Test
    void subscriptions() {
        webTestClient
                .get()
                .uri("/api/v1/subscriber")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestScenario.class)
                .isEqualTo(Arrays.asList(subscription1, subscription2));
    }

    @Test
    void subscriptionsWithProtocol() {
        webTestClient
                .get()
                .uri("/api/v1/subscriber?protocol=REST")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestScenario.class)
                .isEqualTo(Arrays.asList(subscription2));
    }

    @Test
    void subscriptionsWithStatus() {
        webTestClient
                .get()
                .uri("/api/v1/subscriber?status=COMPLETED")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestScenario.class)
                .isEqualTo(Arrays.asList(subscription1));
    }

    @Test
    void subscriptionsWithEmptyStatus() {
        webTestClient
                .get()
                .uri("/api/v1/subscriber?status=")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestScenario.class)
                .isEqualTo(Arrays.asList(subscription1, subscription2));
    }

    @Test
    void subscriptionsWithStatuses() {
        webTestClient
                .get()
                .uri("/api/v1/subscriber?status=COMPLETED,RUNNING")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestScenario.class)
                .isEqualTo(Arrays.asList(subscription1, subscription2));
    }

    @Test
    void subscriptionsNotFound() {
        when(mirrorSubscriber.getSubscriptions()).thenReturn(Flux.empty());
        webTestClient
                .get()
                .uri("/api/v1/subscriber?protocol=GRPC")
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }

    @Test
    void subscriptionsByName() {
        subscription2.setName(subscription1.getName());
        subscription2.setId(2);
        webTestClient
                .get()
                .uri("/api/v1/subscriber/grpc1")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestScenario.class)
                .isEqualTo(Arrays.asList(subscription1, subscription2));
    }

    @Test
    void subscriptionsByNameWithStatus() {
        subscription2.setName(subscription1.getName());
        subscription2.setId(2);
        webTestClient
                .get()
                .uri("/api/v1/subscriber/grpc1?status=COMPLETED")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestScenario.class)
                .isEqualTo(Arrays.asList(subscription1));
    }

    @Test
    void subscriptionsByNameNotFound() {
        webTestClient
                .get()
                .uri("/api/v1/subscriber/invalid")
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }

    @Test
    void subscription() {
        subscription2.setName(subscription1.getName());
        subscription2.setId(2);
        webTestClient
                .get()
                .uri("/api/v1/subscriber/grpc1/2")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(TestScenario.class)
                .isEqualTo(Arrays.asList(subscription2));
    }

    @Test
    void subscriptionIdNotFound1() {
        webTestClient
                .get()
                .uri("/api/v1/subscriber/grpc1/3")
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }

    @Test
    void subscriptionNameNotFound() {
        webTestClient
                .get()
                .uri("/api/v1/subscriber/invalid/1")
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }
}
