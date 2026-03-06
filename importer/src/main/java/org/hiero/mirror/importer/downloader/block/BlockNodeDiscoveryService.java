// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

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
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

@Service
@CustomLog
@NullMarked
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

        final var statusEndpoint = findBlockNodeEndpoint(endpoints, BlockNodeApi.STATUS);
        final var streamEndpoint = findBlockNodeEndpoint(endpoints, BlockNodeApi.SUBSCRIBE_STREAM);

        if (statusEndpoint.isEmpty() || streamEndpoint.isEmpty()) {
            return Optional.empty();
        }

        final var statusHost = toHost(statusEndpoint.get());
        final var streamHost = toHost(streamEndpoint.get());

        if (statusHost.isEmpty() || streamHost.isEmpty()) {
            return Optional.empty();
        }

        final var props = new BlockNodeProperties();
        props.setHost(statusHost.get());
        props.setStatusHost(statusHost.get());
        props.setStatusPort(statusEndpoint.get().getPort());
        props.setStreamingHost(streamHost.get());
        props.setStreamingPort(streamEndpoint.get().getPort());

        return Optional.of(props);
    }

    private static Optional<RegisteredServiceEndpoint> findBlockNodeEndpoint(
            List<RegisteredServiceEndpoint> endpoints, BlockNodeApi api) {
        return endpoints.stream()
                .filter(e ->
                        e.getBlockNode() != null && api.equals(e.getBlockNode().getEndpointApi()))
                .findFirst();
    }

    private static Optional<String> toHost(RegisteredServiceEndpoint endpoint) {
        if (endpoint.getDomainName() != null && !endpoint.getDomainName().isBlank()) {
            return Optional.of(endpoint.getDomainName().trim());
        }
        if (endpoint.getIpAddress() != null && !endpoint.getIpAddress().isBlank()) {
            return Optional.of(endpoint.getIpAddress().trim());
        }
        return Optional.empty();
    }
}
