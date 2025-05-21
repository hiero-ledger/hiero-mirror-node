// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.transaction.BlockItem;
import org.hiero.mirror.common.domain.transaction.RecordItem;

record BlockItemTransformation(
        BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {}
