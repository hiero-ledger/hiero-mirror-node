// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * The new ledger configuration only exists in its singleton state change, which
 * {@link ClprStateChangeHandler#process} already handles generically (merging a partial update against the cached
 * baseline and persisting it). This class exists only for transaction-type dispatch.
 */
@Named
class ClprUpdateLedgerConfigurationTransactionHandler extends AbstractTransactionHandler {

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRUPDATELEDGERCONFIGURATION;
    }
}
