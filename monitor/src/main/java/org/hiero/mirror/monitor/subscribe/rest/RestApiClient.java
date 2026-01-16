// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.rest;

import jakarta.inject.Named;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.NetworkNodesResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CustomLog
@Named
public class RestApiClient {

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
        return webClientRest
                .get()
                .uri(uri.replace(PREFIX, StringUtils.EMPTY), parameters)
                .retrieve()
                .bodyToMono(responseClass)
                .onErrorResume(Mono::error) // Needed for some reason to avoid onErrorDropped
                .name("rest");
    }

    public Flux<NetworkNode> getNodes() {
        var next = new AtomicReference<>("/network/nodes?limit=25");

        return Flux.defer(() -> retrieve(NetworkNodesResponse.class, next.get())
                        .doOnNext(r ->
                                next.set(r.getLinks() != null ? r.getLinks().getNext() : null))
                        .flatMapIterable(NetworkNodesResponse::getNodes))
                .repeat(() -> StringUtils.isNotBlank(next.get()));
    }

    public Mono<HttpStatusCode> getNetworkStakeStatusCode() {
        return webClientRestJava.get().uri("/network/stake").exchangeToMono(r -> Mono.just(r.statusCode()));
    }
}
