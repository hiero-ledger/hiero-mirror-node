// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.domain;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.grpc.repository.EntityRepository;
import org.hiero.mirror.grpc.repository.TopicMessageRepository;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CustomLog
@Named("grpcDomainBuilder")
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ReactiveDomainBuilder {

    public static final EntityId TOPIC_ID = EntityId.of(
            CommonProperties.getInstance().getShard(),
            CommonProperties.getInstance().getRealm(),
            100);

    private final long now = DomainUtils.now();
    private final EntityRepository entityRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final DomainBuilder domainBuilder;
    private long sequenceNumber = 0L;

    @PostConstruct
    void setup() {
        entityRepository.deleteAll();
        topicMessageRepository.deleteAll();
    }

    public Mono<Entity> entity() {
        return entity(e -> {});
    }

    public Mono<Entity> entity(Consumer<Entity.EntityBuilder<?, ?>> customizer) {
        Entity entity = domainBuilder
                .entity()
                .customize(e -> e.id(TOPIC_ID.getId())
                        .num(TOPIC_ID.getNum())
                        .realm(TOPIC_ID.getRealm())
                        .shard(TOPIC_ID.getShard())
                        .type(EntityType.TOPIC))
                .customize(customizer)
                .get();
        return insert(entity).thenReturn(entity);
    }

    public Mono<TopicMessage> topicMessage() {
        return topicMessage(t -> {});
    }

    /**
     * Generates a Topic Message and its matching Transaction with sane defaults and inserts them into the database. The
     * consensusTimestamp and sequenceNumber auto-increase by one on each call.
     *
     * @param customizer allows one to customize the TopicMessage before it is inserted
     * @return the inserted TopicMessage
     */
    public Mono<TopicMessage> topicMessage(Consumer<TopicMessage.TopicMessageBuilder> customizer) {
        TopicMessage topicMessage = domainBuilder
                .topicMessage()
                .customize(e -> e.consensusTimestamp(now + sequenceNumber)
                        .sequenceNumber(++sequenceNumber)
                        .topicId(TOPIC_ID))
                .customize(customizer)
                .get();
        return insert(topicMessage)
                .then(transaction(t -> t.consensusTimestamp(topicMessage.getConsensusTimestamp())
                        .entityId(topicMessage.getTopicId())))
                .thenReturn(topicMessage);
    }

    public Flux<TopicMessage> topicMessages(long count, long startTime) {
        List<Publisher<TopicMessage>> publishers = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            long consensusTimestamp = startTime + i;
            publishers.add(topicMessage(t -> t.consensusTimestamp(consensusTimestamp)));
        }
        return Flux.concat(publishers);
    }

    /**
     * Generates a Transaction and inserts it into the database. Note by default the transaction type is
     * CONSENSUSSUBMITMESSAGE.
     *
     * @param customizer allows one to customize the Transaction before it is inserted
     * @return the inserted Transaction
     */
    public Mono<Transaction> transaction(Consumer<Transaction.TransactionBuilder> customizer) {
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.type(TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId()))
                .customize(customizer)
                .persist();
        return Mono.just(transaction);
    }

    public EntityId entityId() {
        return domainBuilder.entityId();
    }

    private Mono<Entity> insert(Entity entity) {
        return Mono.defer(() -> Mono.just(entityRepository.save(entity))).doOnNext(t -> log.trace("Inserted: {}", t));
    }

    private Mono<TopicMessage> insert(TopicMessage topicMessage) {
        return Mono.defer(() -> Mono.just(topicMessageRepository.save(topicMessage)))
                .doOnNext(t -> log.trace("Inserted: {}", t));
    }
}
