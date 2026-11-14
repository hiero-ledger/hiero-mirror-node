// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceReason;
import com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.entity.EntityType;

class ClprMaintenanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprMaintenanceTransactionHandler();
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
}
