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
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.reader.block.BlockRootHashDigest;

public final class BlockGenerator {

    private static final byte[] ALL_ZERO_HASH = new byte[48];

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    private long blockNumber;
    private byte[] previousBlockRootHash;

    public BlockGenerator(long startBlockNumber) {
        blockNumber = startBlockNumber;
        if (blockNumber == 0) {
            previousBlockRootHash = ALL_ZERO_HASH;
        } else {
            previousBlockRootHash = recordItemBuilder.randomBytes(48);
        }
    }

    public List<BlockItemSet> next(int count) {
        var blocks = new ArrayList<BlockItemSet>();
        for (int i = 0; i < count; i++) {
            blocks.add(next());
        }
        return blocks;
    }

    @SneakyThrows
    private void calculateBlockRootHash(BlockItemSet block) {
        var blockRootHashDigest = new BlockRootHashDigest();
        blockRootHashDigest.setPreviousHash(previousBlockRootHash);
        blockRootHashDigest.setStartOfBlockStateHash(ALL_ZERO_HASH);
        block.getBlockItemsList().forEach(blockRootHashDigest::addBlockItem);
        previousBlockRootHash = Hex.decodeHex(blockRootHashDigest.digest());
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
                .setBlockProof(BlockProof.newBuilder()
                        .setBlock(blockNumber)
                        .setPreviousBlockRootHash(DomainUtils.fromBytes(previousBlockRootHash))
                        .setStartOfBlockStateRootHash(DomainUtils.fromBytes(ALL_ZERO_HASH))));
        var block = builder.build();
        calculateBlockRootHash(block);
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
