// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.TransactionId;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.hiero.mirror.monitor.MirrorNodeProperties;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.monitor.ScenarioProtocol;
import org.hiero.mirror.monitor.ScenarioStatus;
import org.hiero.mirror.monitor.publish.PublishRequest;
import org.hiero.mirror.monitor.publish.PublishResponse;
import org.hiero.mirror.monitor.publish.PublishScenario;
import org.hiero.mirror.monitor.publish.PublishScenarioProperties;
import org.hiero.mirror.monitor.publish.transaction.TransactionType;
import org.hiero.mirror.monitor.subscribe.Scenario;
import org.hiero.mirror.monitor.subscribe.SubscribeProperties;
import org.hiero.mirror.monitor.subscribe.SubscribeResponse;
import org.hiero.mirror.rest.model.TransactionByIdResponse;
import org.hiero.mirror.rest.model.TransactionDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RestSubscriberTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SCENARIO = "test";

    static {
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(Instant.class, new InstantToStringSerializer());
        OBJECT_MAPPER.registerModule(simpleModule);
    }

    @Mock
    private ExchangeFunction exchangeFunction;

    private PublishScenario publishScenario;
    private SubscribeProperties subscribeProperties;
    private RestSubscriberProperties restSubscriberProperties;
    private RestSubscriber restSubscriber;

    @BeforeEach
    void setup() {
        var monitorProperties = new MonitorProperties();
        monitorProperties.setMirrorNode(new MirrorNodeProperties());
        monitorProperties.getMirrorNode().getRest().setHost("127.0.0.1");

        restSubscriberProperties = new RestSubscriberProperties();
        restSubscriberProperties.setLimit(3L);
        restSubscriberProperties.setName("test");
        restSubscriberProperties.getRetry().setMaxAttempts(2L);
        restSubscriberProperties.getRetry().setMinBackoff(Duration.ofNanos(1L));
        restSubscriberProperties.getRetry().setMaxBackoff(Duration.ofNanos(2L));

        var publishScenarioProperties = new PublishScenarioProperties();
        publishScenarioProperties.setName(SCENARIO);
        publishScenarioProperties.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);
        publishScenario = new PublishScenario(publishScenarioProperties);

        subscribeProperties = new SubscribeProperties();
        subscribeProperties.getRest().put(restSubscriberProperties.getName(), restSubscriberProperties);

        var builder = WebClient.builder().exchangeFunction(exchangeFunction);
        var restApiClient = new RestApiClient(monitorProperties, builder);
        restSubscriber = new RestSubscriber(restApiClient, subscribeProperties);
    }

    @Test
    void onPublishWhenComplete() {
        restSubscriber.getSubscriptions().subscribe(s -> s.onComplete());

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verifyNoInteractions(exchangeFunction);
        RestSubscription subscription = restSubscriber.getSubscriptions().blockFirst();
        assertThat(subscription.getCount()).isZero();
    }

    @Test
    void onPublishWhenNoPublisherMatches() {
        restSubscriberProperties.setPublishers(Set.of("invalid"));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500L));

        verifyNoInteractions(exchangeFunction);
        RestSubscription subscription = restSubscriber.getSubscriptions().blockFirst();
        assertThat(subscription.getCount()).isZero();
    }

    @Test
    void onPublishWhenPublisherMatches() {
        restSubscriberProperties.setPublishers(Set.of(SCENARIO));
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(1L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction, times(1)).exchange(Mockito.isA(ClientRequest.class));
        RestSubscription subscription = restSubscriber.getSubscriptions().blockFirst();
        assertThat(subscription.getCount()).isEqualTo(1L);
    }

    @Test
    void subscribe() {
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK));

        Collection<SubscribeResponse> responses = new ArrayList<>();
        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe().doOnNext(responses::add))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(2L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction, times(2)).exchange(Mockito.isA(ClientRequest.class));
        RestSubscription subscription = restSubscriber.getSubscriptions().blockFirst();
        assertThat(subscription)
                .isNotNull()
                .returns(2L, Scenario::getCount)
                .returns(Map.of(), Scenario::getErrors)
                .returns(ScenarioProtocol.REST, Scenario::getProtocol);
        assertThat(responses).hasSize(2).allSatisfy(s -> {
            assertThat(s.getConsensusTimestamp()).isNotNull();
            assertThat(s.getPublishedTimestamp()).isNotNull();
            assertThat(s.getReceivedTimestamp()).isNotNull();
            assertThat(s.getScenario()).isNotNull().isEqualTo(subscription);
        });
    }

    @Test
    void multipleSubscribers() {
        restSubscriberProperties.setSubscribers(2);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(4L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction, times(4)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().collectList().block())
                .hasSize(2)
                .allSatisfy(s -> assertThat(s)
                        .isNotNull()
                        .returns(2L, Scenario::getCount)
                        .returns(Map.of(), Scenario::getErrors)
                        .returns(restSubscriberProperties, Scenario::getProperties)
                        .returns(ScenarioProtocol.REST, Scenario::getProtocol))
                .extracting(Scenario::getId)
                .containsExactly(1, 2);
    }

    @Test
    void multipleScenarios() {
        RestSubscriberProperties restSubscriberProperties2 = new RestSubscriberProperties();
        restSubscriberProperties2.setName("test2");
        subscribeProperties.getRest().put(restSubscriberProperties2.getName(), restSubscriberProperties2);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(4L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction, times(4)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().collectList().block())
                .hasSize(2)
                .doesNotHaveDuplicates()
                .allSatisfy(s -> assertThat(s)
                        .isNotNull()
                        .returns(2L, Scenario::getCount)
                        .returns(Map.of(), Scenario::getErrors))
                .extracting(Scenario::getName)
                .doesNotHaveDuplicates();
    }

    @Test
    void disabled() {
        subscribeProperties.setEnabled(false);
        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verifyNoInteractions(exchangeFunction);
        assertThat(restSubscriber.getSubscriptions().count().block()).isZero();
    }

    @Test
    void disabledScenario() {
        restSubscriberProperties.setEnabled(false);
        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verifyNoInteractions(exchangeFunction);
        assertThat(restSubscriber.getSubscriptions().count().block()).isZero();
    }

    @Test
    void subscribeEmpty() {
        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verifyNoInteractions(exchangeFunction);
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(0L, Scenario::getCount)
                .returns(Map.of(), Scenario::getErrors)
                .returns(ScenarioProtocol.REST, Scenario::getProtocol)
                .returns(ScenarioStatus.IDLE, Scenario::getStatus);
    }

    @Test
    void duration() {
        restSubscriberProperties.setDuration(Duration.ofSeconds(1));
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK))
                .thenReturn(response(HttpStatus.OK).delayElement(Duration.ofSeconds(5L)));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .expectNextCount(1L)
                .thenAwait(Duration.ofSeconds(10L))
                .expectComplete()
                .verify(Duration.ofSeconds(2L));

        verify(exchangeFunction, times(2)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(1L, Scenario::getCount)
                .returns(Map.of(), Scenario::getErrors)
                .returns(ScenarioProtocol.REST, Scenario::getProtocol)
                .returns(ScenarioStatus.COMPLETED, Scenario::getStatus);
    }

    @Test
    void limitReached() {
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(3L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction, times(3)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(3L, Scenario::getCount)
                .returns(ScenarioStatus.COMPLETED, Scenario::getStatus);
    }

    @Test
    void limitExceeded() {
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(3L)
                .expectComplete()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction, times(3)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst()).isNotNull().returns(3L, Scenario::getCount);
    }

    @Test
    void nonRetryableError() {
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.INTERNAL_SERVER_ERROR));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(0L, Scenario::getCount)
                .returns(Map.of("500", 1), Scenario::getErrors);
    }

    @Test
    void recovers() {
        Mockito.when(exchangeFunction.exchange(Mockito.isA(ClientRequest.class)))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.OK));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(1L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction, times(2)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(1L, Scenario::getCount)
                .returns(Map.of(), Scenario::getErrors);
    }

    @Test
    void neverRecovers() {
        Mockito.when(exchangeFunction.exchange(Mockito.isA(ClientRequest.class)))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.NOT_FOUND))
                .thenReturn(response(HttpStatus.NOT_FOUND));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> restSubscriber.onPublish(publishResponse()))
                .thenAwait(Duration.ofSeconds(10L))
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction, times(3)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(0L, Scenario::getCount)
                .returns(Map.of("404", 1), Scenario::getErrors);
    }

    @Test
    void samplePercent0() {
        restSubscriberProperties.setLimit(1000L);
        restSubscriberProperties.setSamplePercent(0.0);

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> {
                    for (int i = 0; i < restSubscriberProperties.getLimit(); ++i) {
                        restSubscriber.onPublish(publishResponse());
                    }
                })
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verifyNoInteractions(exchangeFunction);
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(0L, Scenario::getCount)
                .returns(Map.of(), Scenario::getErrors);
    }

    @Test
    void samplePercent75() {
        restSubscriberProperties.setLimit(256L);
        restSubscriberProperties.setSamplePercent(0.75);
        int min = (int) (restSubscriberProperties.getLimit() * (restSubscriberProperties.getSamplePercent() - 0.5));
        int max = (int) (restSubscriberProperties.getLimit() * (restSubscriberProperties.getSamplePercent() + 0.5));
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK))
                .thenReturn(response(HttpStatus.OK));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> {
                    for (int i = 0; i < restSubscriberProperties.getLimit(); ++i) {
                        restSubscriber.onPublish(publishResponse());
                    }
                })
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(min)
                .thenCancel()
                .verify(Duration.ofSeconds(1L));

        verify(exchangeFunction, atLeast(min)).exchange(Mockito.isA(ClientRequest.class));
        verify(exchangeFunction, atMost(max)).exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(Map.of(), Scenario::getErrors)
                .extracting(Scenario::getCount)
                .isNotNull()
                .matches(count -> count >= min && count <= max);
    }

    @Test
    void samplePercent100() {
        restSubscriberProperties.setLimit(256L);
        restSubscriberProperties.setSamplePercent(1.0);
        Mockito.when(exchangeFunction.exchange(Mockito.any(ClientRequest.class)))
                .thenReturn(response(HttpStatus.OK))
                .thenReturn(response(HttpStatus.OK));

        StepVerifier.withVirtualTime(() -> restSubscriber.subscribe())
                .then(() -> {
                    for (int i = 0; i < restSubscriberProperties.getLimit(); ++i) {
                        restSubscriber.onPublish(publishResponse());
                    }
                })
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(restSubscriberProperties.getLimit())
                .thenCancel()
                .verify(Duration.ofSeconds(2L));

        verify(exchangeFunction, times((int) restSubscriberProperties.getLimit()))
                .exchange(Mockito.isA(ClientRequest.class));
        assertThat(restSubscriber.getSubscriptions().blockFirst())
                .isNotNull()
                .returns(Map.of(), Scenario::getErrors)
                .extracting(Scenario::getCount)
                .isEqualTo(restSubscriberProperties.getLimit());
    }

    private PublishResponse publishResponse() {
        return PublishResponse.builder()
                .request(PublishRequest.builder()
                        .scenario(publishScenario)
                        .timestamp(Instant.now())
                        .build())
                .timestamp(Instant.now())
                .transactionId(TransactionId.withValidStart(AccountId.fromString("0.0.1000"), Instant.ofEpochSecond(1)))
                .build();
    }

    private Mono<ClientResponse> response(HttpStatus httpStatus) {
        if (!httpStatus.is2xxSuccessful()) {
            return Mono.defer(() -> Mono.error(
                    WebClientResponseException.create(httpStatus.value(), "", HttpHeaders.EMPTY, null, null)));
        }

        var transaction = new TransactionDetail();
        transaction.setConsensusTimestamp("1.2");

        var response = new TransactionByIdResponse();
        response.addTransactionsItem(transaction);

        try {
            String json = OBJECT_MAPPER.writeValueAsString(response);
            return Mono.just(ClientResponse.create(httpStatus)
                    .header("Content-Type", "application/json")
                    .body(json)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class InstantToStringSerializer extends StdScalarSerializer<Instant> {

        private static final long serialVersionUID = -7958416584497817326L;

        protected InstantToStringSerializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(Instant instant, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeRawValue(instant.getEpochSecond() + "." + instant.getNano());
        }
    }
}
