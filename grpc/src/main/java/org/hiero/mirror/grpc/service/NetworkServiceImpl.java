// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Named;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.grpc.domain.AddressBookFilter;
import org.hiero.mirror.grpc.exception.EntityNotFoundException;
import org.hiero.mirror.grpc.repository.AddressBookRepository;
import org.hiero.mirror.grpc.repository.NetworkNodeRepository;
import org.hiero.mirror.grpc.repository.NodeStakeRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.repeat.RepeatSpec;

@CustomLog
@Named
@RequiredArgsConstructor
@Validated
public class NetworkServiceImpl implements NetworkService {

    static final String INVALID_FILE_ID = "Not a valid address book file";
    private static final long NODE_STAKE_EMPTY_TABLE_TIMESTAMP = 0L;
    private static final TypeReference<java.util.List<ServiceEndpointRow>> SERVICE_ENDPOINTS_TYPE =
            new TypeReference<>() {};

    private final AddressBookProperties addressBookProperties;
    private final AddressBookRepository addressBookRepository;
    private final NetworkNodeRepository networkNodeRepository;
    private final NodeStakeRepository nodeStakeRepository;
    private final SystemEntity systemEntity;
    private final ObjectMapper objectMapper;

    @Qualifier("readOnly")
    private final TransactionOperations transactionOperations;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Set<EntityId> validFileIds =
            Set.of(systemEntity.addressBookFile101(), systemEntity.addressBookFile102());

    @Override
    public Flux<AddressBookEntry> getNodes(AddressBookFilter filter) {
        var fileId = filter.getFileId();
        if (!getValidFileIds().contains(fileId)) {
            throw new IllegalArgumentException(INVALID_FILE_ID);
        }

        long addressBookTimestamp = addressBookRepository
                .findLatestTimestamp(fileId.getId())
                .orElseThrow(() -> new EntityNotFoundException(fileId));
        long nodeStakeTimestamp = nodeStakeRepository.findLatestTimestamp().orElse(NODE_STAKE_EMPTY_TABLE_TIMESTAMP);
        var nodeStakeMap = nodeStakeRepository.findAllStakeByConsensusTimestamp(nodeStakeTimestamp);
        var context = new AddressBookContext(addressBookTimestamp, nodeStakeMap);

        return Flux.defer(() -> page(context))
                .repeatWhen(RepeatSpec.create(c -> !context.isComplete(), Long.MAX_VALUE)
                        .jitter(0.5)
                        .withFixedDelay(addressBookProperties.getPageDelay())
                        .withScheduler(Schedulers.boundedElastic()))
                .take(filter.getLimit() > 0 ? filter.getLimit() : Long.MAX_VALUE)
                .doOnNext(context::onNext)
                .doOnSubscribe(s -> log.info("Querying for address book: {}", filter))
                .doOnComplete(() -> log.info("Retrieved {} nodes from the address book", context.getCount()));
    }

    private Flux<AddressBookEntry> page(AddressBookContext context) {
        return Objects.requireNonNull(transactionOperations.execute(t -> {
            var addressBookTimestamp = context.getAddressBookTimestamp();
            var nodeStakeMap = context.getNodeStakeMap();
            var nextNodeId = context.getNextNodeId();
            var pageSize = addressBookProperties.getPageSize();
            var nodeViews = networkNodeRepository.findByConsensusTimestampAndMinNodeId(
                    addressBookTimestamp, nextNodeId, pageSize);
            var endpoints = new AtomicInteger(0);

            var nodes = nodeViews.stream()
                    .map(v -> AddressBookEntry.builder()
                            .consensusTimestamp(v.consensusTimestamp())
                            .nodeId(v.nodeId())
                            .description(v.description())
                            .memo(v.memo())
                            .nodeAccountId(EntityId.of(v.nodeAccountId()))
                            .nodeCertHash(v.nodeCertHash())
                            .publicKey(v.publicKey())
                            .stake(v.stake())
                            .build())
                    .toList();

            nodes.forEach(node -> {
                // Override node stake
                node.setStake(nodeStakeMap.getOrDefault(node.getNodeId(), 0L));

                // Populate service endpoints from JSON produced by SQL subquery (rest-java pattern)
                var view = nodeViews.stream()
                        .filter(v -> v.nodeId() == node.getNodeId())
                        .findFirst()
                        .orElse(null);
                if (view != null) {
                    var serviceEndpoints =
                            parseServiceEndpoints(view.serviceEndpointsJson(), addressBookTimestamp, node.getNodeId());
                    node.getServiceEndpoints().addAll(serviceEndpoints);
                    endpoints.addAndGet(serviceEndpoints.size());
                }
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
        }));
    }

    private Set<AddressBookServiceEndpoint> parseServiceEndpoints(String json, long consensusTimestamp, long nodeId) {
        if (json == null || json.isBlank() || "null".equalsIgnoreCase(json)) {
            return java.util.Set.of();
        }
        try {
            var rows = objectMapper.readValue(json, SERVICE_ENDPOINTS_TYPE);
            if (rows == null || rows.isEmpty()) {
                return java.util.Set.of();
            }
            var set = new java.util.LinkedHashSet<AddressBookServiceEndpoint>(rows.size());
            for (var row : rows) {
                set.add(AddressBookServiceEndpoint.builder()
                        .consensusTimestamp(consensusTimestamp)
                        .nodeId(nodeId)
                        .ipAddressV4(row.ipAddressV4())
                        .port(row.port())
                        .domainName(row.domainName())
                        .build());
            }
            return set;
        } catch (Exception e) {
            log.warn("Unable to parse serviceEndpoints JSON: {}", json, e);
            return java.util.Set.of();
        }
    }

    private record ServiceEndpointRow(
            @JsonProperty("domain_name") String domainName,
            @JsonProperty("ip_address_v4") String ipAddressV4,
            @JsonProperty("port") Integer port) {}

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
