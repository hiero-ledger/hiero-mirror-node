// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
class ScheduleSignTransactionHandler extends AbstractTransactionHandler {

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getScheduleSign().getScheduleID());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.SCHEDULESIGN;
    }
}
