// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.importer.repository.NodeRepository;
import org.hiero.mirror.importer.repository.RegisteredNodeRepository;

@Named
@CustomLog
@RequiredArgsConstructor
public class BlockNodeDiscoveryService {

    public static final int TIER_ONE_REGISTERED_NODE_PRIORITY = 0;
    private final NodeRepository nodeRepository;
    private final RegisteredNodeRepository registeredNodeRepository;

    /**
     * Discover block node properties from tier1 registered nodes.
     */
    public List<BlockNodeProperties> discover() {
        try {
            final var registeredNodeIds = nodeRepository.findAllAssociatedRegisteredNodeIds();
            if (registeredNodeIds.isEmpty()) {
                return Collections.emptyList();
            }

            final var registeredNodes =
                    registeredNodeRepository.findAllByRegisteredNodeIdInAndDeletedFalse(registeredNodeIds);

            final List<BlockNodeProperties> propertiesList = new ArrayList<>(registeredNodes.size());
            for (final var node : registeredNodes) {
                toBlockNodeProperties(node).ifPresent(props -> {
                    props.setPriority(TIER_ONE_REGISTERED_NODE_PRIORITY);
                    propertiesList.add(props);
                });
            }

            return propertiesList;
        } catch (Exception ex) {
            log.error("Error during block nodes discovery: ", ex);
            return Collections.emptyList();
        }
    }

    private Optional<BlockNodeProperties> toBlockNodeProperties(RegisteredNode registeredNode) {
        final var endpoints = registeredNode.getServiceEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            return Optional.empty();
        }

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
            }
            if (statusEndpoint != null && streamEndpoint != null) break;
        }
        if (statusEndpoint == null || streamEndpoint == null) {
            return Optional.empty();
        }

        final var statusHost = extractHost(statusEndpoint);
        final var streamHost = extractHost(streamEndpoint);
        if (statusHost == null || streamHost == null) {
            return Optional.empty();
        }

        final var props = new BlockNodeProperties();
        props.setHost(statusHost);
        props.setStatusHost(statusHost);
        props.setStatusPort(statusEndpoint.getPort());
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
