// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
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

    public List<RegisteredNodeType> getTypes() {
        final var types = new ArrayList<RegisteredNodeType>();
        if (blockNode != null) {
            types.add(RegisteredNodeType.BLOCK_NODE);
        }
        if (mirrorNode != null) {
            types.add(RegisteredNodeType.MIRROR_NODE);
        }
        if (rpcRelay != null) {
            types.add(RegisteredNodeType.RPC_RELAY);
        }
        return types;
    }
}
