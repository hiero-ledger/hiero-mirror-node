// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * {@code ClprMaintenance} carries no body of its own -- the finalized manifest only exists in its endpoint-manifest
 * singleton state change, which {@link ClprStateChangeHandler#process} already handles generically (merging a
 * partial update against the cached baseline and persisting it). This class exists only for transaction-type
 * dispatch.
 */
@Named
class ClprMaintenanceTransactionHandler extends AbstractTransactionHandler {

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRMAINTENANCE;
    }
}
