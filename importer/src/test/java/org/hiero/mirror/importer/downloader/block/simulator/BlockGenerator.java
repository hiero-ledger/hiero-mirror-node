// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.simulator;

import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import java.util.ArrayList;
import java.util.List;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;

public final class BlockGenerator {

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    private long blockNumber;

    public BlockGenerator(long startBlockNumber) {
        blockNumber = startBlockNumber;
    }

    public List<BlockItemSet> next(int count) {
        var blocks = new ArrayList<BlockItemSet>();
        for (int i = 0; i < count; i++) {
            blocks.add(next());
        }
        return blocks;
    }

    private BlockItemSet next() {
        var builder = BlockItemSet.newBuilder();
        // block header
        builder.addBlockItems(BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder()
                        .setBlockTimestamp(recordItemBuilder.timestamp())
                        .setNumber(blockNumber)
                        .build()));
        // round header
        builder.addBlockItems(BlockItem.newBuilder()
                .setRoundHeader(
                        RoundHeader.newBuilder().setRoundNumber(blockNumber + 1).build()));
        // event header
        builder.addBlockItems(BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance()));

        // transactions
        for (int i = 0; i < 10; i++) {
            builder.addAllBlockItems(transactionUnit());
        }

        // block proof
        builder.addBlockItems(BlockItem.newBuilder()
                .setBlockProof(BlockProof.newBuilder().setBlock(blockNumber))
                .build());
        var block = builder.build();
        blockNumber++;
        return block;
    }

    private List<BlockItem> transactionUnit() {
        var recordItem = recordItemBuilder.cryptoTransfer().build();
        var signedTransaction = SignedTransaction.newBuilder()
                .setBodyBytes(recordItem.getTransaction().getSignedTransactionBytes())
                .setSigMap(recordItem.getSignatureMap())
                .build();
        var eventTransaction = BlockItem.newBuilder()
                .setSignedTransaction(signedTransaction.toByteString())
                .build();
        var transactionResult = BlockItem.newBuilder()
                .setTransactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(recordItem.getTransactionRecord().getConsensusTimestamp())
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .setTransferList(recordItem.getTransactionRecord().getTransferList())
                        .build())
                .build();
        // for simplicity, no state changes / trace data
        return List.of(eventTransaction, transactionResult);
    }
}
