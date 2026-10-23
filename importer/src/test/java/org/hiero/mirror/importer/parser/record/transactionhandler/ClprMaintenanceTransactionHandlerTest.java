// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceReason;
import com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceTransactionBody;
import com.hederahashgraph.api.proto.java.ClprEndpoint;
import com.hederahashgraph.api.proto.java.ClprServiceEndpoint;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.util.Version;

class ClprMaintenanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprMaintenanceTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprMaintenance(ClprMaintenanceTransactionBody.newBuilder()
                        .setReason(ClprMaintenanceReason.ENDPOINT_MANIFEST_RECONCILIATION));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransaction() {
        // given
        var transactionBody = getDefaultTransactionBody().build();
        var endpoint = ClprEndpoint.newBuilder()
                .setServiceEndpoint(ClprServiceEndpoint.newBuilder()
                        .setIpAddress("10.0.0.1")
                        .setPort(50211))
                .setTlsCertificate(DomainUtils.fromBytes(domainBuilder.bytes(64)))
                .setAccountId(DomainUtils.fromBytes(domainBuilder.bytes(20)))
                .build();
        var manifest = com.hederahashgraph.api.proto.java.ClprEndpointManifest.newBuilder()
                .setVersion(3)
                .setServiceAddress(DomainUtils.fromBytes(domainBuilder.bytes(20)))
                .addEndpoints(endpoint)
                .build();
        var recordItem =
                buildRecordItem(transactionBody, getDefaultTransactionRecord().build(), manifest);
        var transaction = domainBuilder.transaction().get();
        var captor = ArgumentCaptor.forClass(ClprEndpointManifest.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onClprEndpointManifest(captor.capture());
        assertThat(captor.getValue())
                .returns(recordItem.getConsensusTimestamp(), ClprEndpointManifest::getConsensusTimestamp)
                .returns(manifest.getVersion(), ClprEndpointManifest::getVersion)
                .returns(DomainUtils.toBytes(manifest.getServiceAddress()), ClprEndpointManifest::getServiceAddress)
                .extracting(ClprEndpointManifest::getEndpoints)
                .satisfies(endpoints -> assertThat(endpoints).hasSize(1).first().satisfies(actual -> {
                    assertThat(actual.getServiceEndpoint().ipAddress()).isEqualTo("10.0.0.1");
                    assertThat(actual.getServiceEndpoint().port()).isEqualTo(50211);
                    assertThat(actual.getTlsCertificate()).isEqualTo(DomainUtils.toBytes(endpoint.getTlsCertificate()));
                    assertThat(actual.getAccountId()).isEqualTo(DomainUtils.toBytes(endpoint.getAccountId()));
                }));
    }

    @Test
    void updateTransactionMissingStateChange() {
        // given
        var recordItem = buildRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build(),
                null);
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
    }

    private RecordItem buildRecordItem(
            TransactionBody body,
            TransactionRecord transactionRecord,
            com.hederahashgraph.api.proto.java.ClprEndpointManifest manifest) {
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .setSigMap(getDefaultSigMap())
                        .build()
                        .toByteString())
                .build();
        return RecordItem.builder()
                .entityTransactionPredicate(entityProperties.getPersist()::shouldPersistEntityTransaction)
                .hapiVersion(new Version(0, 28, 0))
                .transaction(transaction)
                .transactionRecord(transactionRecord)
                .clprEndpointManifest(manifest)
                .build();
    }
}
