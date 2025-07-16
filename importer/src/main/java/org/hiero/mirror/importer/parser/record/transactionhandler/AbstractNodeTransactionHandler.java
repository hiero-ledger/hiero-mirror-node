// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.node.ServiceEndpoint;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

@RequiredArgsConstructor
public abstract class AbstractNodeTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    public abstract Node parseNode(RecordItem recordItem);

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        transaction.setTransactionBytes(recordItem.getTransaction().toByteArray());
        transaction.setTransactionRecordBytes(recordItem.getTransactionRecord().toByteArray());

        var node = parseNode(recordItem);
        if (node != null) {
            entityListener.onNode(node);
        }
    }

    protected final ServiceEndpoint toServiceEndpoint(
            long consensusTimestamp, com.hederahashgraph.api.proto.java.ServiceEndpoint proto) {

        // This won't happen for node create since consensus nodes reject default ServiceEndpoint
        if (com.hederahashgraph.api.proto.java.ServiceEndpoint.getDefaultInstance()
                .equals(proto)) {
            return ServiceEndpoint.CLEAR;
        }

        String ipAddress = StringUtils.EMPTY;

        try {
            var bytes = DomainUtils.toBytes(proto.getIpAddressV4());
            if (ArrayUtils.isNotEmpty(bytes)) {
                ipAddress = InetAddress.getByAddress(bytes).getHostAddress();
            }
        } catch (UnknownHostException e) {
            Utility.handleRecoverableError(
                    "Unable to parse IP address {} for node create transaction {}: {}",
                    proto.getIpAddressV4(),
                    consensusTimestamp,
                    e.getMessage());
        }

        return ServiceEndpoint.builder()
                .domainName(proto.getDomainName())
                .ipAddressV4(ipAddress)
                .port(proto.getPort())
                .build();
    }
}
