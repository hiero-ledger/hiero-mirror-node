// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.TransactionType;

interface BlockItemTransformer {

    void transform(BlockItemTransformation blockItemTransformation);

    TransactionType getType();
}
