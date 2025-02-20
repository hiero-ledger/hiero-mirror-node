// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hederahashgraph.api.proto.java.TokenID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractTokenTransformer extends AbstractBlockItemTransformer {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    void updateTotalSupply(
            long consensusTimestamp,
            RecordItem.RecordItemBuilder recordItemBuilder,
            StateChangeContext stateChangeContext,
            TokenID tokenId,
            long change) {
        var receiptBuilder = recordItemBuilder.transactionRecordBuilder().getReceiptBuilder();
        stateChangeContext
                .trackTokenTotalSupply(tokenId, change)
                .ifPresentOrElse(
                        receiptBuilder::setNewTotalSupply,
                        () -> log.warn(
                                "Unable to get token {}'s total supply for {} transaction at {}",
                                EntityId.of(tokenId),
                                getType(),
                                consensusTimestamp));
    }
}
