// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import org.hiero.mirror.common.domain.transaction.TransactionType;

interface BlockTransactionTransformer {

    void transform(BlockTransactionTransformation blockTransactionTransformation);

    TransactionType getType();
}
