// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.StateChangeContext;
import com.hederahashgraph.api.proto.java.TokenID;

abstract class AbstractTokenTransformer extends AbstractBlockItemTransformer {

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
