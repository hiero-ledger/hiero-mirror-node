// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.TokenID;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext;
import org.hiero.mirror.importer.util.Utility;

abstract class AbstractTokenTransformer extends AbstractBlockTransactionTransformer {

    void updateTotalSupply(
            final long change,
            final long consensusTimestamp,
            final RecordItem.RecordItemBuilder recordItemBuilder,
            final StateChangeContext stateChangeContext,
            final TokenID tokenId) {
        if (stateChangeContext.isTokenTotalSupplyUnknown(tokenId)) {
            recordItemBuilder.tokenTotalSupplyUnknown(true);
            return;
        }

        final var totalSupply = stateChangeContext.trackTokenTotalSupply(tokenId, change);
        if (totalSupply == null) {
            Utility.handleRecoverableError(
                    "Token {} total supply not found in state changes at {}", tokenId, consensusTimestamp);
            // Don't let the parser overwrite the persisted total supply with the receipt's default of 0
            recordItemBuilder.tokenTotalSupplyUnknown(true);
            return;
        }

        recordItemBuilder.transactionRecordBuilder().getReceiptBuilder().setNewTotalSupply(totalSupply);
    }
}
