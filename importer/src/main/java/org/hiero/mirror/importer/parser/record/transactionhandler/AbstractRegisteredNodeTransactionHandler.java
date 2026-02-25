// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.MirrorNodeEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.RpcRelayEndpoint;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

@RequiredArgsConstructor
public abstract class AbstractRegisteredNodeTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    public abstract RegisteredNode parseRegisteredNode(RecordItem recordItem);

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        transaction.setTransactionBytes(recordItem.getTransaction().toByteArray());
        transaction.setTransactionRecordBytes(recordItem.getTransactionRecord().toByteArray());

        final var registeredNode = parseRegisteredNode(recordItem);
        if (registeredNode != null) {
            entityListener.onRegisteredNode(registeredNode);
        }
    }

    protected final RegisteredServiceEndpoint toRegisteredServiceEndpoint(
            com.hederahashgraph.api.proto.java.RegisteredServiceEndpoint proto) {
        String ipAddress = null;
        String domainName = null;
        switch (proto.getAddressCase()) {
            case IP_ADDRESS -> {
                final var bytes = DomainUtils.toBytes(proto.getIpAddress());
                if (ArrayUtils.isNotEmpty(bytes)) {
                    try {
                        ipAddress = InetAddress.getByAddress(bytes).getHostAddress();
                    } catch (UnknownHostException e) {
                        Utility.handleRecoverableError("Unable to parse IP address: {}", e.getMessage());
                    }
                }
            }
            case DOMAIN_NAME -> domainName = proto.getDomainName();
            default -> {}
        }

        RegisteredServiceEndpoint.BlockNodeEndpoint blockNode = null;
        MirrorNodeEndpoint mirrorNode = null;
        RpcRelayEndpoint rpcRelay = null;
        switch (proto.getEndpointTypeCase()) {
            case BLOCK_NODE ->
                blockNode = BlockNodeEndpoint.builder()
                        .endpointApi(toBlockNodeApi(proto.getBlockNode().getEndpointApi()))
                        .build();
            case MIRROR_NODE -> mirrorNode = MirrorNodeEndpoint.builder().build();
            case RPC_RELAY -> rpcRelay = RpcRelayEndpoint.builder().build();
            default -> {}
        }

        return RegisteredServiceEndpoint.builder()
                .ipAddress(ipAddress)
                .domainName(domainName)
                .port(proto.getPort())
                .requiresTls(proto.getRequiresTls())
                .blockNode(blockNode)
                .mirrorNode(mirrorNode)
                .rpcRelay(rpcRelay)
                .build();
    }

    private static BlockNodeApi toBlockNodeApi(
            com.hederahashgraph.api.proto.java.RegisteredServiceEndpoint.BlockNodeEndpoint.BlockNodeApi proto) {
        try {
            return BlockNodeApi.valueOf(proto.name());
        } catch (IllegalArgumentException e) {
            return BlockNodeApi.OTHER;
        }
    }
}
