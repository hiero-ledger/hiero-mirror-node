// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.TokenID;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext;

abstract class AbstractTokenTransformer extends AbstractBlockTransactionTransformer {

    void updateTotalSupply(
            RecordItem.RecordItemBuilder recordItemBuilder,
            StateChangeContext stateChangeContext,
            TokenID tokenId,
            long change) {
        var receiptBuilder = recordItemBuilder.transactionRecordBuilder().getReceiptBuilder();
        receiptBuilder.setNewTotalSupply(
                stateChangeContext.trackTokenTotalSupply(tokenId, change).orElseThrow());
    }
}
