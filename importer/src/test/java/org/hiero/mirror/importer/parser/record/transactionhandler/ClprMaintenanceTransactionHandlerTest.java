// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceReason.ENDPOINT_MANIFEST_RECONCILIATION_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.clpr.ClprMaintenance;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClprMaintenanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprMaintenanceTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprMaintenance(ClprMaintenanceTransactionBody.newBuilder()
                        .setReason(
                                com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceReason
                                        .ENDPOINT_MANIFEST_RECONCILIATION));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransaction() {
        // given
        final var recordItem = getRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build());
        final var transaction = domainBuilder.transaction().get();
        final var captor = ArgumentCaptor.forClass(ClprMaintenance.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onClprMaintenance(captor.capture());
        assertThat(captor.getValue())
                .returns(recordItem.getConsensusTimestamp(), ClprMaintenance::getConsensusTimestamp)
                .returns(ENDPOINT_MANIFEST_RECONCILIATION_VALUE, ClprMaintenance::getReason);
    }
}
