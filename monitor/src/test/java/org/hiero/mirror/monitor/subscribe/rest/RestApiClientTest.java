// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import org.hiero.mirror.monitor.MirrorNodeProperties;
import org.hiero.mirror.monitor.MirrorNodeProperties.RestProperties;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.NetworkNodesResponse;
import org.hiero.mirror.rest.model.TransactionByIdResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RestApiClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration WAIT = Duration.ofSeconds(10L);

    @Mock
    private ExchangeFunction exchangeFunction;

    private MonitorProperties monitorProperties;
    private RestApiClient restApiClient;

    @BeforeEach
    void setup() {
        monitorProperties = new MonitorProperties();
        monitorProperties.setMirrorNode(new MirrorNodeProperties());
        monitorProperties.getMirrorNode().getRest().setHost("127.0.0.1");

        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        restApiClient = new RestApiClient(monitorProperties, builder);
    }

    @Test
    void getNodes() {
        var next = "/network/nodes?limit=25";
        NetworkNode networkNode1 = new NetworkNode();
        NetworkNode networkNode2 = new NetworkNode();
        NetworkNode networkNode3 = new NetworkNode();
        var response1 = new NetworkNodesResponse()
                .links(new Links().next(next + "&node.id=gt:1"))
                .nodes(List.of(networkNode1, networkNode2));
        var response2 = new NetworkNodesResponse().links(new Links()).nodes(List.of(networkNode3));

        when(exchangeFunction.exchange(isA(ClientRequest.class)))
                .thenReturn(response(response1))
                .thenReturn(response(response2));

        StepVerifier.withVirtualTime(() -> restApiClient.getNodes())
                .thenAwait(WAIT)
                .expectNext(networkNode1, networkNode2, networkNode3)
                .expectComplete()
                .verify(WAIT);

        verify(exchangeFunction, times(2)).exchange(isA(ClientRequest.class));
    }

    @Test
    void getNodesEmpty() {
        var response = new NetworkNodesResponse().links(new Links()).nodes(List.of());
        when(exchangeFunction.exchange(isA(ClientRequest.class))).thenReturn(response(response));

        StepVerifier.withVirtualTime(() -> restApiClient.getNodes())
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        verify(exchangeFunction).exchange(isA(ClientRequest.class));
    }

    @Test
    void getNodesEmptyBody() {
        when(exchangeFunction.exchange(isA(ClientRequest.class)))
                .thenAnswer(_ -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .build()));

        StepVerifier.withVirtualTime(() -> restApiClient.getNodes())
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        verify(exchangeFunction).exchange(isA(ClientRequest.class));
    }

    @Test
    void getNodesEmptyPageWithNext() {
        final var response = new NetworkNodesResponse()
                .links(new Links().next("/api/v1/network/nodes?limit=25&node.id=gt:1"))
                .nodes(List.of());
        when(exchangeFunction.exchange(isA(ClientRequest.class))).thenAnswer(_ -> response(response));

        StepVerifier.withVirtualTime(() -> restApiClient.getNodes())
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        verify(exchangeFunction).exchange(isA(ClientRequest.class));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://169.254.169.254/latest/meta-data/",
                "http://example.com/api/v1/network/nodes?limit=25",
                "https://example.com:8080/network/nodes",
                "//example.com/api/v1/network/nodes",
                "/api/v1/network/supply",
                "/api/v1/network/nodes/../supply",
                "/api/v1/network/nodes?limit={limit}",
                "\\\\example.com\\network\\nodes",
                "network/nodes"
            })
    void getNodesInvalidNext(String next) {
        final var networkNode = new NetworkNode();
        final var response =
                new NetworkNodesResponse().links(new Links().next(next)).nodes(List.of(networkNode));
        final var request = ArgumentCaptor.forClass(ClientRequest.class);
        when(exchangeFunction.exchange(request.capture())).thenAnswer(_ -> response(response));

        StepVerifier.withVirtualTime(() -> restApiClient.getNodes())
                .thenAwait(WAIT)
                .expectNext(networkNode)
                .expectComplete()
                .verify(WAIT);

        assertThat(request.getAllValues())
                .extracting(r -> r.url().toString())
                .containsExactly("https://127.0.0.1:443/api/v1/network/nodes?limit=25");
    }

    @Test
    void getNodesMaxPages() {
        final var networkNode = new NetworkNode();
        final var response = new NetworkNodesResponse()
                .links(new Links().next("/api/v1/network/nodes?limit=25&node.id=gt:1"))
                .nodes(List.of(networkNode));
        when(exchangeFunction.exchange(isA(ClientRequest.class))).thenAnswer(_ -> response(response));

        StepVerifier.withVirtualTime(() -> restApiClient.getNodes())
                .thenAwait(WAIT)
                .expectNextCount(10L)
                .expectComplete()
                .verify(WAIT);

        verify(exchangeFunction, times(10)).exchange(isA(ClientRequest.class));
    }

    @Test
    void getNodesNextWithPrefix() {
        final var networkNode1 = new NetworkNode().nodeId(1L);
        final var networkNode2 = new NetworkNode().nodeId(2L);
        final var response1 = new NetworkNodesResponse()
                .links(new Links().next("/api/v1/network/nodes?limit=25&node.id=gt:1"))
                .nodes(List.of(networkNode1));
        final var response2 = new NetworkNodesResponse().links(new Links()).nodes(List.of(networkNode2));
        final var request = ArgumentCaptor.forClass(ClientRequest.class);
        when(exchangeFunction.exchange(request.capture()))
                .thenReturn(response(response1))
                .thenReturn(response(response2));

        StepVerifier.withVirtualTime(() -> restApiClient.getNodes())
                .thenAwait(WAIT)
                .expectNext(networkNode1, networkNode2)
                .expectComplete()
                .verify(WAIT);

        assertThat(request.getAllValues())
                .extracting(r -> r.url().toString())
                .containsExactly(
                        "https://127.0.0.1:443/api/v1/network/nodes?limit=25",
                        "https://127.0.0.1:443/api/v1/network/nodes?limit=25&node.id=gt:1");
    }

    @Test
    void getNodesResubscribe() {
        final var networkNode = new NetworkNode();
        final var response = new NetworkNodesResponse().links(new Links()).nodes(List.of(networkNode));
        when(exchangeFunction.exchange(isA(ClientRequest.class))).thenAnswer(_ -> response(response));
        final var nodes = restApiClient.getNodes();

        StepVerifier.create(nodes).expectNext(networkNode).expectComplete().verify(WAIT);
        StepVerifier.create(nodes).expectNext(networkNode).expectComplete().verify(WAIT);

        verify(exchangeFunction, times(2)).exchange(isA(ClientRequest.class));
    }

    @Test
    void retrieve() {
        var response = new TransactionByIdResponse();
        when(exchangeFunction.exchange(isA(ClientRequest.class))).thenReturn(response(response));

        StepVerifier.withVirtualTime(() ->
                        restApiClient.retrieve(TransactionByIdResponse.class, "transactions/{transactionId}", "1.1"))
                .thenAwait(WAIT)
                .expectNext(response)
                .expectComplete()
                .verify(WAIT);

        verify(exchangeFunction).exchange(isA(ClientRequest.class));
    }

    @Test
    void retrieveConnectError() {
        restApiClient = new RestApiClient(monitorProperties, WebClient.builder());
        StepVerifier.withVirtualTime(() ->
                        restApiClient.retrieve(TransactionByIdResponse.class, "transactions/{transactionId}", "1.1"))
                .thenAwait(WAIT)
                .expectErrorMatches(t -> t.getCause() instanceof ConnectException)
                .verify(WAIT);
    }

    @Test
    void getTransactionsStatusCode() {
        when(exchangeFunction.exchange(isA(ClientRequest.class)))
                .thenReturn(Mono.just(
                        ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));

        StepVerifier.withVirtualTime(() -> restApiClient.getTransactionsStatusCode())
                .thenAwait(WAIT)
                .expectNext(HttpStatusCode.valueOf(503))
                .expectComplete()
                .verify(WAIT);

        verify(exchangeFunction).exchange(isA(ClientRequest.class));
    }

    @Test
    void restJava() {
        var restJavaProperties = new RestProperties();
        restJavaProperties.setHost("rest-java");
        monitorProperties.getMirrorNode().setRestJava(restJavaProperties);
        var builder = WebClient.builder().exchangeFunction(exchangeFunction);
        restApiClient = new RestApiClient(monitorProperties, builder);

        when(exchangeFunction.exchange(isA(ClientRequest.class)))
                .thenReturn(Mono.just(
                        ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));

        StepVerifier.withVirtualTime(() -> restApiClient.getTransactionsStatusCode())
                .thenAwait(WAIT)
                .expectNext(HttpStatusCode.valueOf(503))
                .expectComplete()
                .verify(WAIT);

        verify(exchangeFunction).exchange(isA(ClientRequest.class));
    }

    private Mono<ClientResponse> response(Object response) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(response);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(json)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
