// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import com.google.common.collect.Iterables;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
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

    private static final List<BlockNodeProperties> CLEARED = Collections.emptyList();

    private final BlockProperties blockProperties;
    private final AtomicReference<List<BlockNodeProperties>> cache = new AtomicReference<>(CLEARED);
    private final RegisteredNodeRepository registeredNodeRepository;

    /**
     * Returns a sorted and deduplicated list of block nodes properties, combination of config file properties and
     * auto-discovered ones (read from cache or database). Auto-discovered properties will override config file
     * properties when they represent the same block node (same status endpoint (host+port) and requiresTls, and
     * same streaming endpoint (host+port) and requiresTls).
     * The result is cached. Cache is invalidated when registered nodes are created, updated, or deleted.
     */
    public List<BlockNodeProperties> getBlockNodesConfigProperties() {
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
                    RegisteredNodeType.BLOCK_NODE.getValue());

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
     * Returns the properties of tier 1 block nodes (block nodes that have
     * PUBLISH_API, STATUS_API, and SUBSCRIBE_STREAM_API endpoints).
     */
    private static Optional<BlockNodeProperties> toBlockNodeProperties(
            final List<RegisteredServiceEndpoint> endpoints) {
        if (CollectionUtil.isEmpty(endpoints)) {
            return Optional.empty();
        }

        boolean hasPublishEndpoint = false;
        RegisteredServiceEndpoint statusEndpoint = null;
        RegisteredServiceEndpoint streamEndpoint = null;
        for (final var endpoint : endpoints) {
            if (endpoint.getBlockNode() == null) {
                continue;
            }

            // Always pick the first of each required endpoint type
            switch (endpoint.getBlockNode().getEndpointApi()) {
                case PUBLISH -> hasPublishEndpoint = true;
                case STATUS -> statusEndpoint = statusEndpoint == null ? endpoint : statusEndpoint;
                case SUBSCRIBE_STREAM -> streamEndpoint = streamEndpoint == null ? endpoint : streamEndpoint;
            }

            if (hasPublishEndpoint && statusEndpoint != null && streamEndpoint != null) {
                return toBlockNodeProperties(statusEndpoint, streamEndpoint);
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockNodeProperties> toBlockNodeProperties(
            final RegisteredServiceEndpoint statusEndpoint, final RegisteredServiceEndpoint streamEndpoint) {
        final var statusHost = extractHost(statusEndpoint);
        final var streamHost = extractHost(streamEndpoint);

        if (statusHost == null || streamHost == null) {
            return Optional.empty();
        }

        final var properties = new BlockNodeProperties();
        properties.setHost(statusHost);
        properties.setStatusApiRequireTls(statusEndpoint.isRequiresTls());
        properties.setStatusPort(statusEndpoint.getPort());
        properties.setStreamingApiRequireTls(streamEndpoint.isRequiresTls());
        properties.setStreamingHost(streamHost);
        properties.setStreamingPort(streamEndpoint.getPort());

        return Optional.of(properties);
    }
}
