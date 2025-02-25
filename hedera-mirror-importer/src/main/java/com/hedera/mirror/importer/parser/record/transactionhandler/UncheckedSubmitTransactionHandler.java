// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
class UncheckedSubmitTransactionHandler extends AbstractTransactionHandler {

    @Override
    public TransactionType getType() {
        return TransactionType.UNCHECKEDSUBMIT;
    }
}
