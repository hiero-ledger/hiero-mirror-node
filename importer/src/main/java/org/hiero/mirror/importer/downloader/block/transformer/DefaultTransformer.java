// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class DefaultTransformer extends AbstractBlockTransactionTransformer {

    /**
     * Any transaction type that has no operations for the updateTransactionRecord method can use this transformer
     */
    @Override
    public TransactionType getType() {
        return TransactionType.UNKNOWN;
    }
}
