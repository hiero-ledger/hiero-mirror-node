// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
final class DefaultTransformer extends AbstractBlockItemTransformer {

    /**
     * Any transaction type that has no operations for the updateTransactionRecord method can use this transformer
     */
    @Override
    public TransactionType getType() {
        return TransactionType.UNKNOWN;
    }
}
