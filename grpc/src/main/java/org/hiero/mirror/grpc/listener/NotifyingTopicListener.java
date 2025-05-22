// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.micrometer.observation.ObservationRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.pubsub.PgChannel;
import io.vertx.pgclient.pubsub.PgSubscriber;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Objects;
import org.hiero.mirror.common.converter.EntityIdDeserializer;
import org.hiero.mirror.common.converter.EntityIdSerializer;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.DbProperties;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

@Named
public class NotifyingTopicListener extends SharedTopicListener {

    final ObjectMapper objectMapper;
    private final Mono<PgChannel> channel;
    private final JdbcConnectionDetails connectionDetails;
    private final DbProperties dbProperties;
    private final Flux<TopicMessage> topicMessages;

    public NotifyingTopicListener(
            JdbcConnectionDetails connectionDetails,
            DbProperties dbProperties,
            ListenerProperties listenerProperties,
            ObservationRegistry observationRegistry) {
        super(listenerProperties);
        this.connectionDetails = connectionDetails;
        this.dbProperties = dbProperties;

        // use EntityIdDeserializer/EntityIdSerializer for EntityIds (e.g. payer_account_id)
        var module = new SimpleModule();
        module.addDeserializer(EntityId.class, EntityIdDeserializer.INSTANCE);
        module.addSerializer(EntityIdSerializer.INSTANCE);

        objectMapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.registerModule(module);

        channel = Mono.defer(this::createChannel).cache();
        Duration interval = listenerProperties.getInterval();
        topicMessages = Flux.defer(this::listen)
                .map(this::toTopicMessage)
                .filter(Objects::nonNull)
                .name(METRIC)
                .tag(METRIC_TAG, "notify")
                .tap(Micrometer.observation(observationRegistry))
                .doOnError(t -> log.error("Error listening for messages", t))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, interval).maxBackoff(interval.multipliedBy(4L)))
                .share();
    }

    @Override
    protected Flux<TopicMessage> getSharedListener(TopicMessageFilter filter) {
        return topicMessages;
    }

    private Flux<String> listen() {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        return channel.doOnNext(c -> c.handler(sink::tryEmitNext))
                .doOnNext(c -> log.info("Listening for messages"))
                .flatMapMany(c -> sink.asFlux())
                .doFinally(s -> unListen());
    }

    private void unListen() {
        channel.subscribe(c -> c.handler(null));
        log.info("Stopped listening for messages");
    }

    private Mono<PgChannel> createChannel() {
        var uri = connectionDetails.getJdbcUrl().replace("jdbc:", "");
        var connectOptions = PgConnectOptions.fromUri(uri)
                .setPassword(dbProperties.getPassword())
                .setUser(dbProperties.getUsername());

        Duration interval = listenerProperties.getInterval();
        VertxOptions vertxOptions = new VertxOptions();
        vertxOptions.getFileSystemOptions().setFileCachingEnabled(false);
        vertxOptions.getFileSystemOptions().setClassPathResolvingEnabled(false);
        Vertx vertx = Vertx.vertx(vertxOptions);

        PgSubscriber subscriber = PgSubscriber.subscriber(vertx, connectOptions).reconnectPolicy(retries -> {
            log.warn("Attempting reconnect");
            return interval.toMillis() * Math.min(retries, 4);
        });

        return Mono.fromCompletionStage(subscriber.connect().toCompletionStage())
                .doOnSuccess(v -> log.info("Connected to database"))
                .thenReturn(subscriber.channel("topic_message"));
    }

    private TopicMessage toTopicMessage(String payload) {
        try {
            return objectMapper.readValue(payload, TopicMessage.class);
        } catch (Exception ex) {
            // Discard invalid messages. No need to propagate error and cause a reconnect.
            log.error("Error parsing message {}", payload, ex);
            return null;
        }
    }
}
