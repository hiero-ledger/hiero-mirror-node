// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeEndpoints;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.GeneralServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.MirrorNodeEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.RpcRelayEndpoint;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.RegisteredNodeChangedEvent;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.context.ApplicationEventPublisher;

@RequiredArgsConstructor
abstract class AbstractRegisteredNodeTransactionHandler extends AbstractTransactionHandler {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final EntityListener entityListener;

    protected abstract RegisteredNode parseRegisteredNode(final RecordItem recordItem);

    @Override
    protected void doUpdateTransaction(final Transaction transaction, final RecordItem recordItem) {
        final var registeredNode = parseRegisteredNode(recordItem);
        if (registeredNode == null) {
            return;
        }

        entityListener.onRegisteredNode(registeredNode);
        if (registeredNode.isDeleted() || !CollectionUtils.isEmpty(registeredNode.getServiceEndpoints())) {
            applicationEventPublisher.publishEvent(new RegisteredNodeChangedEvent(this));
        }
    }

    protected static void parseServiceEndpoints(
            final RegisteredNode.RegisteredNodeBuilder<?, ?> builder,
            final List<com.hederahashgraph.api.proto.java.RegisteredServiceEndpoint> protoServiceEndpoints) {
        final var serviceEndpoints = new ArrayList<RegisteredServiceEndpoint>(protoServiceEndpoints.size());
        final var types = new TreeSet<Short>();
        for (final var protoServiceEndpoint : protoServiceEndpoints) {
            final var endpoint = toRegisteredServiceEndpoint(protoServiceEndpoint);
            serviceEndpoints.add(endpoint);
            types.add(endpoint.getType().getId());
        }

        builder.serviceEndpoints(serviceEndpoints);
        builder.type(List.copyOf(types));
    }

    protected static RegisteredServiceEndpoint toRegisteredServiceEndpoint(
            final com.hederahashgraph.api.proto.java.RegisteredServiceEndpoint proto) {
        final var builder =
                RegisteredServiceEndpoint.builder().port(proto.getPort()).requiresTls(proto.getRequiresTls());
        switch (proto.getAddressCase()) {
            case IP_ADDRESS -> {
                final var bytes = DomainUtils.toBytes(proto.getIpAddress());
                if (ArrayUtils.isNotEmpty(bytes)) {
                    try {
                        builder.ipAddress(InetAddress.getByAddress(bytes).getHostAddress());
                    } catch (UnknownHostException e) {
                        Utility.handleRecoverableError("Unable to parse IP address: {}", e.getMessage());
                    }
                }
            }
            case DOMAIN_NAME -> builder.domainName(proto.getDomainName());
            default -> Utility.handleRecoverableError("Invalid addressCase: {}", proto.getAddressCase());
        }

        switch (proto.getEndpointTypeCase()) {
            case BLOCK_NODE ->
                builder.blockNode(BlockNodeEndpoints.builder()
                        .endpointApisList(proto.getBlockNode().getEndpointApiList().stream()
                                .map(AbstractRegisteredNodeTransactionHandler::toBlockNodeApi)
                                .collect(Collectors.toList()))
                        .build());
            case GENERAL_SERVICE ->
                builder.generalService(GeneralServiceEndpoint.builder()
                        .description(proto.getGeneralService().getDescription())
                        .build());
            case MIRROR_NODE -> builder.mirrorNode(MirrorNodeEndpoint.INSTANCE);
            case RPC_RELAY -> builder.rpcRelay(RpcRelayEndpoint.INSTANCE);
            default -> Utility.handleRecoverableError("Invalid endpointTypeCase: {}", proto.getEndpointTypeCase());
        }

        return builder.build();
    }

    private static BlockNodeApi toBlockNodeApi(
            final com.hederahashgraph.api.proto.java.RegisteredServiceEndpoint.BlockNodeEndpoint.BlockNodeApi proto) {
        return switch (proto) {
            case OTHER -> BlockNodeApi.OTHER;
            case PUBLISH -> BlockNodeApi.PUBLISH;
            case STATE_PROOF -> BlockNodeApi.STATE_PROOF;
            case STATUS -> BlockNodeApi.STATUS;
            case SUBSCRIBE_STREAM -> BlockNodeApi.SUBSCRIBE_STREAM;
            case UNRECOGNIZED -> {
                Utility.handleRecoverableError("Unrecognized BlockNodeApi enum value");
                yield BlockNodeApi.UNRECOGNIZED;
            }
        };
    }
}
