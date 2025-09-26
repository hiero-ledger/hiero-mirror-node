// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;

record BlockTransactionTransformation(
        BlockTransaction blockTransaction, RecordItem.RecordItemBuilder recordItemBuilder) {

    TransactionBody getTransactionBody() {
        return blockTransaction.getTransactionBody();
    }
}
