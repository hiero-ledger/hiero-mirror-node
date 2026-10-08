// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.rest;

import jakarta.inject.Named;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.NetworkNodesResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@CustomLog
@Named
public class RestApiClient {

    private static final String NETWORK = "/network/";
    private static final String NODES_PATH = "/network/nodes";
    private static final String PREFIX = "/api/v1";

    private final WebClient webClientRest;
    private final WebClient webClientRestJava;

    public RestApiClient(MonitorProperties monitorProperties, WebClient.Builder webClientBuilder) {
        final var rest = monitorProperties.getMirrorNode().getRest();
        final var restJava = monitorProperties.getMirrorNode().getRestJava();
        final var restUrl = rest.getBaseUrl();
        final var restJavaUrl = restJava != null ? restJava.getBaseUrl() : rest.getBaseUrl();
        webClientRest = webClientBuilder
                .baseUrl(restUrl)
                .defaultHeaders(h -> h.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();
        webClientRestJava = Objects.equals(restUrl, restJavaUrl)
                ? webClientRest
                : webClientRest.mutate().baseUrl(restJavaUrl).build();
        log.info("Connecting to mirror node REST API {}", restUrl);
        log.info("Connecting to mirror node REST Java API {}", restJavaUrl);
    }

    public <T> Mono<T> retrieve(Class<T> responseClass, String uri, Object... parameters) {
        final var webClient = uri.contains(NETWORK) ? webClientRestJava : webClientRest;
        return webClient
                .get()
                .uri(uri.replace(PREFIX, StringUtils.EMPTY), parameters)
                .retrieve()
                .bodyToMono(responseClass)
                .onErrorResume(Mono::error) // Needed for some reason to avoid onErrorDropped
                .name("rest")
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<NetworkNode> getNodes() {
        return Flux.defer(() -> {
            final var next = new AtomicReference<>(NODES_PATH + "?limit=25");

            return Flux.defer(() -> retrieve(NetworkNodesResponse.class, next.getAndSet(null))
                            .doOnNext(r -> next.set(getNextNodesUri(r)))
                            .flatMapIterable(NetworkNodesResponse::getNodes))
                    .repeat(() -> next.get() != null);
        });
    }

    public Mono<HttpStatusCode> getTransactionsStatusCode() {
        return webClientRest
                .get()
                .uri("/transactions?limit=1&order=desc")
                .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode()));
    }

    /**
     * Returns the URI of the next page of nodes, or null if pagination should stop. The next link comes from the
     * response body, so it is only followed when it's a relative reference to the nodes endpoint. This ensures a
     * response can never redirect the monitor to a different host or endpoint. An empty page also ends pagination.
     */
    private String getNextNodesUri(final NetworkNodesResponse response) {
        final var links = response.getLinks();
        final var next = links != null ? links.getNext() : null;

        if (StringUtils.isBlank(next) || CollectionUtils.isEmpty(response.getNodes())) {
            return null;
        }

        if (!isNodesUri(next)) {
            log.warn("Stopping node pagination due to invalid next link");
            return null;
        }

        return next;
    }

    private static boolean isNodesUri(final String uri) {
        try {
            final var parsed = new URI(uri);
            return parsed.getScheme() == null
                    && parsed.getRawAuthority() == null
                    && NODES_PATH.equals(Strings.CS.removeStart(parsed.getRawPath(), PREFIX));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
