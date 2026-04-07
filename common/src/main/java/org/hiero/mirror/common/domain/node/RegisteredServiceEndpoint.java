// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    private GeneralServiceEndpoint generalService;
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

    @Data
    @Builder
    public static class GeneralServiceEndpoint {
        private String description;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MirrorNodeEndpoint {
        public static final MirrorNodeEndpoint INSTANCE = new MirrorNodeEndpoint();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RpcRelayEndpoint {
        public static final RpcRelayEndpoint INSTANCE = new RpcRelayEndpoint();
    }

    @JsonIgnore
    public RegisteredNodeType getType() {
        if (blockNode != null) {
            return RegisteredNodeType.BLOCK_NODE;
        } else if (generalService != null) {
            return RegisteredNodeType.GENERAL_SERVICE;
        } else if (mirrorNode != null) {
            return RegisteredNodeType.MIRROR_NODE;
        } else if (rpcRelay != null) {
            return RegisteredNodeType.RPC_RELAY;
        }

        return RegisteredNodeType.UNKNOWN;
    }
}
