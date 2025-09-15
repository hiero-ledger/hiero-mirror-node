// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
public class BlockTransactionTransformerFactory {

    private final BlockTransactionTransformer defaultTransformer;
    private final Map<TransactionType, BlockTransactionTransformer> transformers;

    BlockTransactionTransformerFactory(List<BlockTransactionTransformer> transformers) {
        this.transformers = transformers.stream()
                .collect(Collectors.toUnmodifiableMap(BlockTransactionTransformer::getType, Function.identity()));
        this.defaultTransformer = this.transformers.get(TransactionType.UNKNOWN);
    }

    public void transform(BlockTransaction blockTransaction, RecordItem.RecordItemBuilder builder) {
        var transactionBody = blockTransaction.getTransactionBody();
        var blockItemTransformer = get(transactionBody);
        // pass transactionBody for performance
        blockItemTransformer.transform(new BlockTransactionTransformation(blockTransaction, builder));
    }

    private BlockTransactionTransformer get(TransactionBody transactionBody) {
        var transactionType = TransactionType.of(transactionBody.getDataCase().getNumber());
        return transformers.getOrDefault(transactionType, defaultTransformer);
    }
}
