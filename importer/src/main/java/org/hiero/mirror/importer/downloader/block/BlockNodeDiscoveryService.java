// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.importer.parser.record.RegisteredNodeChangedEvent;
import org.hiero.mirror.importer.repository.RegisteredNodeRepository;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Discovers block node properties from registered nodes and merges them with configured nodes.
 * RegisteredNodeChangedEvent is produced on registered node create,update and delete.
 * Results are cached to avoid unnecessary database queries; cache is invalidated when registered
 * nodes are created, updated, or deleted.
 */
@Named
@CustomLog
@RequiredArgsConstructor
public class BlockNodeDiscoveryService {

    public static final int TIER_ONE_REGISTERED_NODE_PRIORITY = 0;
    private final RegisteredNodeRepository registeredNodeRepository;

    private final AtomicReference<List<BlockNodeProperties>> blockNodesCache = new AtomicReference<>();

    /**
     * Returns a list of block nodes properties, combination of config file properties and
     * auto-discovered ones (read from cache or database). Auto-discovered properties will override config file
     * properties if they have the same streaming endpoints.
     */
    public List<BlockNodeProperties> getBlockNodesConfigProperties(BlockProperties blockProperties) {
        final Map<String, BlockNodeProperties> configurationsMap = new HashMap<>();

        for (final var configFileNodesProperties : blockProperties.getNodes()) {
            configurationsMap.put(configFileNodesProperties.getStreamingEndpoint(), configFileNodesProperties);
        }
        if (blockProperties.isAutoDiscoveryEnabled()) {
            final var autoDiscoveredNodesProperties = discover();
            for (final var blockNodeProperties : autoDiscoveredNodesProperties) {
                configurationsMap.put(blockNodeProperties.getStreamingEndpoint(), blockNodeProperties);
            }
        }

        return new ArrayList<>(configurationsMap.values());
    }

    /**
     * Returns cached block nodes properties if available, otherwise returns all tier 1 block nodes properties from db.
     */
    public List<BlockNodeProperties> discover() {
        final var cached = blockNodesCache.get();
        if (cached != null) {
            return cached;
        }

        try {
            final var serviceEndpointsList = registeredNodeRepository.findServiceEndpointsByDeletedFalseAndTypeContains(
                    RegisteredNodeType.BLOCK_NODE.getValue());

            final List<BlockNodeProperties> propertiesList = new ArrayList<>(serviceEndpointsList.size());
            for (final var nodeServiceEndpoints : serviceEndpointsList) {
                toBlockNodeProperties(nodeServiceEndpoints.getServiceEndpoints())
                        .ifPresent(props -> {
                            props.setPriority(TIER_ONE_REGISTERED_NODE_PRIORITY);
                            propertiesList.add(props);
                        });
            }

            final var result = Collections.unmodifiableList(propertiesList);
            blockNodesCache.set(result);
            return result;
        } catch (Exception ex) {
            log.error("Error during block nodes discovery: ", ex);
            return Collections.emptyList();
        }
    }

    @TransactionalEventListener(RegisteredNodeChangedEvent.class)
    public void onRegisteredNodeChanged(@SuppressWarnings("unused") RegisteredNodeChangedEvent event) {
        blockNodesCache.set(null);
        log.debug("Invalidated block node discovery cache");
    }

    /**
     * Returns the properties of tier 1 block nodes (block nodes that have
     * PUBLISH_API, STATUS_API, and SUBSCRIBE_STREAM_API endpoints).
     */
    private Optional<BlockNodeProperties> toBlockNodeProperties(List<RegisteredServiceEndpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return Optional.empty();
        }

        RegisteredServiceEndpoint publishEndpoint = null;
        RegisteredServiceEndpoint statusEndpoint = null;
        RegisteredServiceEndpoint streamEndpoint = null;
        for (final var endpoint : endpoints) {
            if (endpoint.getBlockNode() == null) {
                continue;
            }
            final var api = endpoint.getBlockNode().getEndpointApi();
            if (api == BlockNodeApi.STATUS && statusEndpoint == null) {
                statusEndpoint = endpoint;
            } else if (api == BlockNodeApi.SUBSCRIBE_STREAM && streamEndpoint == null) {
                streamEndpoint = endpoint;
            } else if (api == BlockNodeApi.PUBLISH && publishEndpoint == null) {
                publishEndpoint = endpoint;
            }
            if (statusEndpoint != null && streamEndpoint != null && publishEndpoint != null) break;
        }
        if (statusEndpoint == null || streamEndpoint == null || publishEndpoint == null) {
            return Optional.empty();
        }

        final var statusHost = extractHost(statusEndpoint);
        final var streamHost = extractHost(streamEndpoint);
        if (statusHost == null || streamHost == null || extractHost(publishEndpoint) == null) {
            return Optional.empty();
        }

        final var props = new BlockNodeProperties();
        props.setHost(statusHost);
        props.setStatusApiRequireTls(statusEndpoint.isRequiresTls());
        props.setStatusHost(statusHost);
        props.setStatusPort(statusEndpoint.getPort());
        props.setStreamingApiRequireTls(streamEndpoint.isRequiresTls());
        props.setStreamingHost(streamHost);
        props.setStreamingPort(streamEndpoint.getPort());

        return Optional.of(props);
    }

    private static String extractHost(RegisteredServiceEndpoint endpoint) {
        final var domainName = endpoint.getDomainName();
        if (domainName != null && !domainName.isBlank()) {
            return domainName.trim();
        }

        final var ipAddress = endpoint.getIpAddress();
        if (ipAddress != null && !ipAddress.isBlank()) {
            return ipAddress.trim();
        }
        return null;
    }
}
