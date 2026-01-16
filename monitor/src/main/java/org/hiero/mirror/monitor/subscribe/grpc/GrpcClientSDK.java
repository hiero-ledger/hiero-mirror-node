// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.grpc;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TopicMessage;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import jakarta.inject.Named;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.monitor.subscribe.SubscribeProperties;
import org.hiero.mirror.monitor.subscribe.SubscribeResponse;
import org.hiero.mirror.monitor.util.Utility;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@CustomLog
@Named
class GrpcClientSDK implements GrpcClient {

    private final Flux<Client> clients;
    private final MonitorProperties monitorProperties;
    private final SecureRandom secureRandom;
    private final SubscribeProperties subscribeProperties;

    GrpcClientSDK(MonitorProperties monitorProperties, SubscribeProperties subscribeProperties) {
        this.monitorProperties = monitorProperties;
        this.secureRandom = new SecureRandom();
        this.subscribeProperties = subscribeProperties;
        clients = Flux.range(0, subscribeProperties.getClients())
                .flatMap(i -> Flux.defer(this::client))
                .cache();

        String endpoint = monitorProperties.getMirrorNode().getGrpc().getEndpoint();
        log.info("Connecting {} clients to {}", subscribeProperties.getClients(), endpoint);
    }

    @Override
    public Flux<SubscribeResponse> subscribe(GrpcSubscription subscription) {
        int clientIndex = secureRandom.nextInt(subscribeProperties.getClients());
        log.info("Starting '{}' scenario to client {}", subscription, clientIndex);
        return clients.elementAt(clientIndex).flatMapMany(client -> subscribeToClient(client, subscription));
    }

    private Flux<SubscribeResponse> subscribeToClient(Client client, GrpcSubscription subscription) {
        Sinks.Many<TopicMessage> sink = Sinks.many().multicast().directBestEffort();

        TopicMessageQuery topicMessageQuery = subscription.getTopicMessageQuery();
        topicMessageQuery.setCompletionHandler(sink::tryEmitComplete);
        topicMessageQuery.setErrorHandler((throwable, topicMessage) -> sink.tryEmitError(throwable));
        topicMessageQuery.setMaxAttempts(0); // Disable since we use our own retry logic to capture errors
        SubscriptionHandle subscriptionHandle = topicMessageQuery.subscribe(client, sink::tryEmitNext);

        return sink.asFlux()
                .publishOn(Schedulers.parallel())
                .doFinally(s -> subscriptionHandle.unsubscribe())
                .doOnComplete(subscription::onComplete)
                .doOnError(subscription::onError)
                .doOnNext(subscription::onNext)
                .map(t -> toResponse(subscription, t));
    }

    private SubscribeResponse toResponse(GrpcSubscription subscription, TopicMessage topicMessage) {
        Instant receivedTimestamp = Instant.now();
        Instant publishedTimestamp = Utility.getTimestamp(topicMessage.contents);

        if (publishedTimestamp == null) {
            log.warn(
                    "{} Invalid published timestamp for message with consensus timestamp {}",
                    subscription,
                    topicMessage.consensusTimestamp);
        }

        return SubscribeResponse.builder()
                .consensusTimestamp(topicMessage.consensusTimestamp)
                .publishedTimestamp(publishedTimestamp)
                .receivedTimestamp(receivedTimestamp)
                .scenario(subscription)
                .build();
    }

    @Override
    public void close() {
        log.warn("Closing {} clients", subscribeProperties.getClients());
        clients.subscribe(client -> {
            try {
                client.close();
            } catch (Exception e) {
                // Ignore
            }
        });
    }

    private Mono<Client> client() {
        String endpoint = monitorProperties.getMirrorNode().getGrpc().getEndpoint();

        try {
            Client client = Client.forNetwork(Map.of());
            client.setMirrorNetwork(List.of(endpoint));
            return Mono.just(client);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to initialize SDK client to " + endpoint);
        }
    }
}
