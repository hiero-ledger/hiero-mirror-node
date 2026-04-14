// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import com.google.common.collect.Iterables;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.importer.parser.record.RegisteredNodeChangedEvent;
import org.hiero.mirror.importer.repository.RegisteredNodeRepository;
import org.identityconnectors.common.CollectionUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Discovers block node properties from registered nodes and merges them with configured nodes.
 * RegisteredNodeChangedEvent is produced on registered node create,update and delete.
 * Results are cached to avoid unnecessary database queries; cache is invalidated when registered
 * nodes are created, updated, or deleted.
 */
@Named
@NullMarked
@CustomLog
@RequiredArgsConstructor
public final class BlockNodeDiscoveryService {

    private static final Set<BlockNodeApi> TIER_ONE_BLOCK_NODE_APIS =
            EnumSet.of(BlockNodeApi.STATUS, BlockNodeApi.PUBLISH, BlockNodeApi.SUBSCRIBE_STREAM);
    private static final List<BlockNodeProperties> CLEARED = Collections.emptyList();

    private final BlockProperties blockProperties;
    private final AtomicReference<List<BlockNodeProperties>> cache = new AtomicReference<>(CLEARED);
    private final RegisteredNodeRepository registeredNodeRepository;

    /**
     * Returns a sorted and deduplicated list of block nodes properties, combination of config file properties and
     * auto-discovered ones (read from cache or database). Auto-discovered properties will override config file
     * properties when they represent the same block node (same endpoint (host+port) and requiresTls).
     * The result is cached. Cache is invalidated when registered nodes are created, updated, or deleted.
     */
    public List<BlockNodeProperties> getBlockNodes() {
        return cache.updateAndGet(propertiesList -> {
            if (propertiesList != CLEARED) {
                return propertiesList;
            }

            final var configurationsMap = new HashMap<String, BlockNodeProperties>();
            for (final var properties : Iterables.concat(blockProperties.getNodes(), discover())) {
                configurationsMap.put(properties.getMergeKey(), properties);
            }

            final var result = new ArrayList<>(configurationsMap.values());
            Collections.sort(result);
            return Collections.unmodifiableList(result);
        });
    }

    /**
     * Returns tier 1 block nodes properties from the database.
     */
    private List<BlockNodeProperties> discover() {
        if (!blockProperties.isAutoDiscoveryEnabled()) {
            return Collections.emptyList();
        }

        try {
            final var nodes = registeredNodeRepository.findAllByDeletedFalseAndTypeContains(
                    RegisteredNodeType.BLOCK_NODE.getId());

            final List<BlockNodeProperties> propertiesList = new ArrayList<>(nodes.size());
            for (final var node : nodes) {
                toBlockNodeProperties(node.getServiceEndpoints()).ifPresent(propertiesList::add);
            }

            return propertiesList;
        } catch (Exception ex) {
            log.error("Error during block nodes discovery: ", ex);
            return Collections.emptyList();
        }
    }

    @TransactionalEventListener(RegisteredNodeChangedEvent.class)
    public void onRegisteredNodeChanged() {
        cache.set(CLEARED);
        log.debug("Invalidated block node discovery cache");
    }

    @Nullable
    private static String extractHost(final RegisteredServiceEndpoint endpoint) {
        final var domainName = endpoint.getDomainName();
        if (!StringUtils.isBlank(domainName)) {
            return domainName.trim();
        }

        final var ipAddress = endpoint.getIpAddress();
        if (!StringUtils.isBlank(ipAddress)) {
            return ipAddress.trim();
        }

        return null;
    }

    /**
     * Returns the properties of tier 1 block nodes. A tier 1 block node is one that has a single
     * endpoint advertising all three required APIs: STATUS, PUBLISH, and SUBSCRIBE_STREAM.
     */
    private static Optional<BlockNodeProperties> toBlockNodeProperties(
            final List<RegisteredServiceEndpoint> endpoints) {
        if (CollectionUtil.isEmpty(endpoints)) {
            return Optional.empty();
        }

        for (final var endpoint : endpoints) {
            if (endpoint.getBlockNode() == null) {
                continue;
            }

            if (!EnumSet.copyOf(endpoint.getBlockNode().getEndpointApis()).containsAll(TIER_ONE_BLOCK_NODE_APIS)) {
                continue;
            }

            final var host = extractHost(endpoint);
            if (host == null) {
                continue;
            }

            final var properties = new BlockNodeProperties();
            properties.setHost(host);
            properties.setPort(endpoint.getPort());
            properties.setRequiresTls(endpoint.isRequiresTls());
            return Optional.of(properties);
        }

        return Optional.empty();
    }
}
