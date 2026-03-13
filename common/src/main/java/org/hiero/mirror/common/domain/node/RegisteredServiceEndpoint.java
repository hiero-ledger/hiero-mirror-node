// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.TreeSet;
import lombok.Builder;
import lombok.Data;

/**
 * Published endpoint for a registered node.
 * Based on registered_service_endpoint.proto
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisteredServiceEndpoint {

    private BlockNodeEndpoint blockNode;
    private String domainName;
    private String ipAddress;
    private MirrorNodeEndpoint mirrorNode;
    private int port;
    private boolean requiresTls;
    private RpcRelayEndpoint rpcRelay;

    @Data
    @Builder
    public static class BlockNodeEndpoint {
        private BlockNodeApi endpointApi;
    }

    public enum BlockNodeApi {
        OTHER,
        STATUS,
        PUBLISH,
        SUBSCRIBE_STREAM,
        STATE_PROOF,
        UNRECOGNIZED
    }

    public static class MirrorNodeEndpoint {}

    public static class RpcRelayEndpoint {}

    /**
     * Returns sorted list of unique registered node type IDs derived from the service endpoints
     */
    public static List<Short> deriveTypesFromEndpoints(List<RegisteredServiceEndpoint> serviceEndpoints) {
        if (serviceEndpoints == null || serviceEndpoints.isEmpty()) {
            return List.of();
        }
        final var types = new TreeSet<Short>();
        for (final var endpoint : serviceEndpoints) {
            if (endpoint.getBlockNode() != null) {
                types.add(RegisteredNodeType.BLOCK_NODE.getId());
            }
            if (endpoint.getMirrorNode() != null) {
                types.add(RegisteredNodeType.MIRROR_NODE.getId());
            }
            if (endpoint.getRpcRelay() != null) {
                types.add(RegisteredNodeType.RPC_RELAY.getId());
            }
        }
        return List.copyOf(types);
    }
}
