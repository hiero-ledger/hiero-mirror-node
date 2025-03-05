// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.exception.ProtobufException;
import com.hederahashgraph.api.proto.java.SignedTransaction;
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
        var transactionBody = parse(blockItem.getTransaction().getSignedTransactionBytes());
        var blockItemTransformer = get(transactionBody);
        // pass transactionBody for performance
        blockItemTransformer.transform(blockItem, builder, transactionBody);
    }

    private BlockItemTransformer get(TransactionBody transactionBody) {
        var transactionType = TransactionType.of(transactionBody.getDataCase().getNumber());
        return transformers.getOrDefault(transactionType, defaultTransformer);
    }

    private TransactionBody parse(ByteString signedTransactionBytes) {
        try {
            var signedTransaction = SignedTransaction.parseFrom(signedTransactionBytes);
            return TransactionBody.parseFrom(signedTransaction.getBodyBytes());
        } catch (InvalidProtocolBufferException e) {
            throw new ProtobufException("Error parsing transaction body from signed transaction bytes", e);
        }
    }
}
