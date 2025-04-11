// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Named
public class BlockItemTransformerFactory {

    private final BlockItemTransformer defaultTransformer;
    private final Map<TransactionType, BlockItemTransformer> transformers;

    BlockItemTransformerFactory(List<BlockItemTransformer> transformers) {
        this.transformers = transformers.stream()
                .collect(Collectors.toUnmodifiableMap(BlockItemTransformer::getType, Function.identity()));
        this.defaultTransformer = this.transformers.get(TransactionType.UNKNOWN);
    }

    public void transform(BlockItem blockItem, RecordItem.RecordItemBuilder builder) {
        var transactionBody = blockItem.getTransactionBody();
        var blockItemTransformer = get(transactionBody);
        // pass transactionBody for performance
        blockItemTransformer.transform(new BlockItemTransformation(blockItem, builder, transactionBody));
    }

    private BlockItemTransformer get(TransactionBody transactionBody) {
        var transactionType = TransactionType.of(transactionBody.getDataCase().getNumber());
        return transformers.getOrDefault(transactionType, defaultTransformer);
    }
}
