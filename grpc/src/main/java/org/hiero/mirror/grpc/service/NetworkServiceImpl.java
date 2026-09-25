// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.service;

import jakarta.inject.Named;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.grpc.domain.AddressBookFilter;
import org.hiero.mirror.grpc.exception.EntityNotFoundException;
import org.hiero.mirror.grpc.exception.SubscriptionLimitException;
import org.hiero.mirror.grpc.exception.SubscriptionTimeoutException;
import org.hiero.mirror.grpc.interceptor.RemoteAddressInterceptor;
import org.hiero.mirror.grpc.repository.AddressBookEntryRepository;
import org.hiero.mirror.grpc.repository.AddressBookRepository;
import org.hiero.mirror.grpc.repository.NodeStakeRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.util.repeat.RepeatSpec;

@CustomLog
@Named
@RequiredArgsConstructor
@Validated
public class NetworkServiceImpl implements NetworkService {

    static final String INVALID_FILE_ID = "Not a valid address book file";
    static final String TOO_MANY_SUBSCRIPTIONS = "Too many concurrent address book subscriptions";
    private static final String ANONYMOUS_CLIENT = "anonymous";
    private static final long NODE_STAKE_EMPTY_TABLE_TIMESTAMP = 0L;
    private static final String STREAM_TIMEOUT = "Address book subscription timed out";

    private final ConcurrentHashMap<String, AtomicInteger> activeSubscriptions = new ConcurrentHashMap<>();
    private final AddressBookProperties addressBookProperties;
    private final AddressBookRepository addressBookRepository;
    private final AddressBookEntryRepository addressBookEntryRepository;
    private final NodeStakeRepository nodeStakeRepository;

    @Qualifier("addressBook")
    private final Scheduler scheduler;

    private final SystemEntity systemEntity;

    @Qualifier("readOnly")
    private final TransactionOperations transactionOperations;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Set<EntityId> validFileIds =
            Set.of(systemEntity.addressBookFile101(), systemEntity.addressBookFile102());

    @Override
    public Flux<AddressBookEntry> getNodes(AddressBookFilter filter) {
        final var fileId = filter.getFileId();
        if (!getValidFileIds().contains(fileId)) {
            throw new IllegalArgumentException(INVALID_FILE_ID);
        }

        final var clientKey = clientKey();
        final var limit = effectiveLimit(filter.getLimit());

        return Flux.defer(() -> {
                    acquire(clientKey);
                    return Mono.fromCallable(() -> loadContext(fileId))
                            .flatMapMany(context -> Flux.defer(() -> page(context))
                                    .repeatWhen(RepeatSpec.create(c -> !context.isComplete(), limit)
                                            .jitter(0.5)
                                            .withFixedDelay(addressBookProperties.getPageDelay())
                                            .withScheduler(scheduler))
                                    .take(limit)
                                    .doOnNext(context::onNext)
                                    .doOnSubscribe(s -> log.info("Querying for address book: {}", filter))
                                    .doOnComplete(() ->
                                            log.info("Retrieved {} nodes from the address book", context.getCount())))
                            .doFinally(signal -> release(clientKey));
                })
                .takeUntilOther(Mono.delay(addressBookProperties.getTimeout(), scheduler)
                        .then(Mono.error(new SubscriptionTimeoutException(STREAM_TIMEOUT))))
                .subscribeOn(scheduler);
    }

    private void acquire(String clientKey) {
        final var limit = addressBookProperties.getMaxConcurrentPerConnection();
        final var admitted = new AtomicBoolean();
        activeSubscriptions.compute(clientKey, (key, active) -> {
            if (active != null && active.get() >= limit) {
                return active;
            }
            admitted.set(true);
            final var next = active == null ? new AtomicInteger() : active;
            next.incrementAndGet();
            return next;
        });
        if (!admitted.get()) {
            throw new SubscriptionLimitException(TOO_MANY_SUBSCRIPTIONS);
        }
    }

    private String clientKey() {
        final var remoteAddress = RemoteAddressInterceptor.REMOTE_ADDRESS.get();
        if (remoteAddress == null) {
            return ANONYMOUS_CLIENT;
        }
        return remoteAddress.toString();
    }

    private int effectiveLimit(int requested) {
        final var maxLimit = addressBookProperties.getMaxLimit();
        if (requested <= 0) {
            return maxLimit;
        }
        if (requested > maxLimit) {
            log.info("Clamping address book limit {} to server maximum {}", requested, maxLimit);
            return maxLimit;
        }
        return requested;
    }

    private AddressBookContext loadContext(EntityId fileId) {
        final long addressBookTimestamp = addressBookRepository
                .findLatestTimestamp(fileId.getId())
                .orElseThrow(() -> new EntityNotFoundException(fileId));
        final long nodeStakeTimestamp =
                nodeStakeRepository.findLatestTimestamp().orElse(NODE_STAKE_EMPTY_TABLE_TIMESTAMP);
        final var nodeStakeMap = nodeStakeRepository.findAllStakeByConsensusTimestamp(nodeStakeTimestamp);
        return new AddressBookContext(addressBookTimestamp, nodeStakeMap);
    }

    private Flux<AddressBookEntry> page(AddressBookContext context) {
        return transactionOperations.execute(t -> {
            var addressBookTimestamp = context.getAddressBookTimestamp();
            var nodeStakeMap = context.getNodeStakeMap();
            var nextNodeId = context.getNextNodeId();
            var pageSize = addressBookProperties.getPageSize();
            var nodes = addressBookEntryRepository.findByConsensusTimestampAndNodeId(
                    addressBookTimestamp, nextNodeId, pageSize);
            var endpoints = new AtomicInteger(0);

            nodes.forEach(node -> {
                // Override node stake
                node.setStake(nodeStakeMap.getOrDefault(node.getNodeId(), 0L));
                // This hack ensures that the nested serviceEndpoints is loaded eagerly and voids lazy init exceptions
                endpoints.addAndGet(node.getServiceEndpoints().size());
            });

            if (nodes.size() < pageSize) {
                context.completed();
            }

            log.info(
                    "Retrieved {} address book entries and {} endpoints for timestamp {} and node ID {}",
                    nodes.size(),
                    endpoints,
                    addressBookTimestamp,
                    nextNodeId);
            return Flux.fromIterable(nodes);
        });
    }

    private void release(String clientKey) {
        activeSubscriptions.compute(clientKey, (key, active) -> {
            if (active == null || active.decrementAndGet() <= 0) {
                return null;
            }
            return active;
        });
    }

    @Value
    private static class AddressBookContext {

        private final AtomicBoolean complete = new AtomicBoolean(false);
        private final AtomicLong count = new AtomicLong(0L);
        private final AtomicReference<AddressBookEntry> last = new AtomicReference<>();
        private final long addressBookTimestamp;
        private final Map<Long, Long> nodeStakeMap;

        void onNext(AddressBookEntry entry) {
            count.incrementAndGet();
            last.set(entry);
        }

        long getNextNodeId() {
            AddressBookEntry entry = last.get();
            return entry != null ? entry.getNodeId() + 1 : 0L;
        }

        boolean isComplete() {
            return complete.get();
        }

        void completed() {
            complete.set(true);
        }
    }
}
