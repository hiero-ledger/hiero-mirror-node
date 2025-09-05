// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.simulator;

import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.platform.event.legacy.EventTransaction;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.reader.block.BlockRootHashDigest;
import org.hiero.mirror.importer.util.Utility;

public final class BlockGenerator {

    private static final byte[] ALL_ZERO_HASH = new byte[48];

    private final Duration interval;
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    private long blockNumber;
    private byte[] previousBlockRootHash;

    public BlockGenerator(long startBlockNumber) {
        this(Duration.ofMillis(1), startBlockNumber, Instant.now());
    }

    public BlockGenerator(Duration interval, long startBlockNumber, Instant startTime) {
        blockNumber = startBlockNumber;
        if (blockNumber == 0) {
            previousBlockRootHash = ALL_ZERO_HASH;
        } else {
            previousBlockRootHash = recordItemBuilder.randomBytes(48);
        }

        recordItemBuilder.setNow(startTime);
        this.interval = interval;
    }

    public List<BlockRecord> next(int count) {
        var blocks = new ArrayList<BlockRecord>();
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

        for (var blockItem : block.getBlockItemsList()) {
            switch (blockItem.getItemCase()) {
                case EVENT_HEADER, EVENT_TRANSACTION, ROUND_HEADER -> blockRootHashDigest.addInputBlockItem(blockItem);
                case BLOCK_HEADER, STATE_CHANGES, TRANSACTION_OUTPUT, TRANSACTION_RESULT ->
                    blockRootHashDigest.addOutputBlockItem(blockItem);
                default -> {
                    // other block items aren't considered input / output
                }
            }
        }

        previousBlockRootHash = Hex.decodeHex(blockRootHashDigest.digest());
    }

    private BlockRecord next() {
        var builder = BlockItemSet.newBuilder();

        // block header
        var blockTimestamp = recordItemBuilder.timestamp(ChronoUnit.NANOS);
        builder.addBlockItems(BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder()
                        .setBlockTimestamp(blockTimestamp)
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
        // set blocks roughly apart, so in latency related tests, streaming latency don't reduce drastically from
        // one block to the next
        recordItemBuilder.setNow(Utility.convertToInstant(blockTimestamp).plus(interval));

        return new BlockRecord(block);
    }

    private List<BlockItem> transactionUnit() {
        var recordItem = recordItemBuilder.cryptoTransfer().build();
        var eventTransaction = BlockItem.newBuilder()
                .setEventTransaction(EventTransaction.newBuilder()
                        .setApplicationTransaction(recordItem.getTransaction().toByteString())
                        .build())
                .build();
        var transactionResult = BlockItem.newBuilder()
                .setTransactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(recordItem.getTransactionRecord().getConsensusTimestamp())
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .setTransferList(recordItem.getTransactionRecord().getTransferList())
                        .build())
                .build();
        // for simplicity, no state changes
        return List.of(eventTransaction, transactionResult);
    }

    public record BlockRecord(BlockItemSet block, AtomicLong latency, AtomicLong readyTime) {
        public BlockRecord(BlockItemSet block) {
            this(block, new AtomicLong(0), new AtomicLong(0));
        }
    }
}
