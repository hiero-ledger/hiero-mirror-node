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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
final class ClprMaintenanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

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

    @Test
    void updateTransactionReasonTooLargeForSmallint(CapturedOutput output) {
        // given -- a raw wire number too large for the smallint column is clamped rather than failing the insert
        final var transactionBody = TransactionBody.newBuilder()
                .setClprMaintenance(ClprMaintenanceTransactionBody.newBuilder().setReasonValue(100_000))
                .build();
        final var recordItem =
                getRecordItem(transactionBody, getDefaultTransactionRecord().build());
        final var transaction = domainBuilder.transaction().get();
        final var captor = ArgumentCaptor.forClass(ClprMaintenance.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onClprMaintenance(captor.capture());
        assertThat(captor.getValue()).returns((int) Short.MAX_VALUE, ClprMaintenance::getReason);
        assertThat(output.getAll()).contains("ClprMaintenance reason 100000 exceeds smallint range");
    }
}
