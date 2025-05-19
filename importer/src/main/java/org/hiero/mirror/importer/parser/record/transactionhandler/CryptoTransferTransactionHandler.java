// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
class CryptoTransferTransactionHandler extends AbstractTransactionHandler {

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOTRANSFER;
    }
}
