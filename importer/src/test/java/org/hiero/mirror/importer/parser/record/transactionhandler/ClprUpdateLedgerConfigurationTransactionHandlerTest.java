// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hederahashgraph.api.proto.java.ClprThrottles;
import com.hederahashgraph.api.proto.java.ClprUpdateLedgerConfigurationTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;

class ClprUpdateLedgerConfigurationTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprUpdateLedgerConfigurationTransactionHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprUpdateLedgerConfiguration(ClprUpdateLedgerConfigurationTransactionBody.newBuilder()
                        .setConfiguration(com.hederahashgraph.api.proto.java.ClprLedgerConfiguration.newBuilder()
                                .setProtocolVersion(1)
                                .setChainId("hedera:mainnet")
                                .setServiceAddress(DomainUtils.fromBytes(domainBuilder.bytes(20)))
                                .setInitialTrustAnchor(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                                .setInitialTrustAnchorId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                                .setThrottles(ClprThrottles.newBuilder()
                                        .setMaxMessagesPerBundle(100)
                                        .setMaxMessagePayloadBytes(8192)
                                        .setMaxGasPerMessage(15_000_000L)
                                        .setMaxQueueDepth(1000)
                                        .setMaxSyncBytes(4_194_304L)
                                        .setMaxLocalEndpoints(39)
                                        .setMaxPeerEndpoints(39))));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }
}
