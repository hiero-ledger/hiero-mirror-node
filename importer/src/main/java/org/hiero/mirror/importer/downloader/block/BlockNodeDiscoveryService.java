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
import org.apache.commons.lang3.StringUtils;
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

    private final BlockProperties blockProperties;
    private final RegisteredNodeRepository registeredNodeRepository;
    private final AtomicReference<List<BlockNodeProperties>> blockNodesConfigCache = new AtomicReference<>();

    /**
     * Returns a sorted and deduplicated list of block nodes properties, combination of config file properties and
     * auto-discovered ones (read from cache or database). Auto-discovered properties will override config file
     * properties when they represent the same block node (same status endpoint (host+port) and requiresTls, and
     * same streaming endpoint (host+port) and requiresTls).
     * Results is cached. Cache is invalidated when registered nodes are created, updated, or deleted.
     */
    public List<BlockNodeProperties> getBlockNodesConfigProperties() {
        final var cached = blockNodesConfigCache.get();
        if (cached != null) {
            return cached;
        }

        final Map<String, BlockNodeProperties> configurationsMap = new HashMap<>();

        for (final var configFileNodesProperties : blockProperties.getNodes()) {
            configurationsMap.put(configFileNodesProperties.getMergeKey(), configFileNodesProperties);
        }
        if (blockProperties.isAutoDiscoveryEnabled()) {
            final var autoDiscoveredNodesProperties = discover();
            for (final var blockNodeProperties : autoDiscoveredNodesProperties) {
                configurationsMap.put(blockNodeProperties.getMergeKey(), blockNodeProperties);
            }
        }

        final var result = new ArrayList<>(configurationsMap.values());
        Collections.sort(result);
        blockNodesConfigCache.set(Collections.unmodifiableList(result));
        return result;
    }

    /**
     * Returns tier 1 block nodes properties from the database.
     */
    private List<BlockNodeProperties> discover() {
        try {
            final var nodes = registeredNodeRepository.findRegisteredNodesByDeletedFalseAndTypeContains(
                    RegisteredNodeType.BLOCK_NODE.getValue());

            final List<BlockNodeProperties> propertiesList = new ArrayList<>(nodes.size());
            for (final var node : nodes) {
                toBlockNodeProperties(node.getServiceEndpoints()).ifPresent(propertiesList::add);
            }

            return Collections.unmodifiableList(propertiesList);
        } catch (Exception ex) {
            log.error("Error during block nodes discovery: ", ex);
            return Collections.emptyList();
        }
    }

    @TransactionalEventListener(RegisteredNodeChangedEvent.class)
    public void onRegisteredNodeChanged() {
        blockNodesConfigCache.set(null);
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

        final var host = extractHost(statusEndpoint);
        final var streamHost = extractHost(streamEndpoint);
        if (host == null || streamHost == null || extractHost(publishEndpoint) == null) {
            return Optional.empty();
        }

        final var props = new BlockNodeProperties();
        props.setHost(host);
        props.setStatusApiRequireTls(statusEndpoint.isRequiresTls());
        props.setStatusPort(statusEndpoint.getPort());
        props.setStreamingApiRequireTls(streamEndpoint.isRequiresTls());
        props.setStreamingHost(streamHost);
        props.setStreamingPort(streamEndpoint.getPort());

        return Optional.of(props);
    }

    private static String extractHost(RegisteredServiceEndpoint endpoint) {
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
}
