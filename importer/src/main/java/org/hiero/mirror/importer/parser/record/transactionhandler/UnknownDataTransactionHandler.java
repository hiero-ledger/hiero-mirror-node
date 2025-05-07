// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
class UnknownDataTransactionHandler extends AbstractTransactionHandler {

    @Override
    public TransactionType getType() {
        return TransactionType.UNKNOWN;
    }
}
