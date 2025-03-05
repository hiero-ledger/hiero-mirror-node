// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;

interface BlockItemTransformer {

    void transform(
            BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody);

    TransactionType getType();
}
