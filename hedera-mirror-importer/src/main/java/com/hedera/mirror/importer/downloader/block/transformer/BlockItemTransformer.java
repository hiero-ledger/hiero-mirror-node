// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;

interface BlockItemTransformer {

    TransactionRecord getTransactionRecord(BlockItem blockItem, TransactionBody transactionBody);

    TransactionType getType();
}
