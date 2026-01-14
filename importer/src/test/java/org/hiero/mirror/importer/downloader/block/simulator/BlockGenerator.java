// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.simulator;

import static org.hiero.mirror.common.util.DomainUtils.fromBytes;

import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockFooter;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockProof;
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
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.reader.block.BlockRootHashDigest;
import org.hiero.mirror.importer.util.Utility;

public final class BlockGenerator {

    private static final byte[] ALL_ZERO_HASH = new byte[48];

    private final Duration interval;
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    private long blockNumber;
    private byte[] previousBlockRootHash;

    public BlockGenerator(final long startBlockNumber) {
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

    public List<BlockRecord> next(final int count) {
        var blocks = new ArrayList<BlockRecord>();
        for (int i = 0; i < count; i++) {
            blocks.add(next());
        }
        return blocks;
    }

    @SneakyThrows
    private void calculateBlockRootHash(BlockItemSet block) {
        var blockRootHashDigest = new BlockRootHashDigest();
        block.getBlockItemsList().forEach(blockRootHashDigest::addBlockItem);
        previousBlockRootHash = Hex.decodeHex(blockRootHashDigest.digest());
    }

    private BlockRecord next() {
        final var builder = BlockItemSet.newBuilder();

        // block header
        final var blockTimestamp = recordItemBuilder.timestamp(ChronoUnit.NANOS);
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
        final var block = builder.addBlockItems(blockFooter(previousBlockRootHash))
                .addBlockItems(blockProof(blockNumber))
                .build();
        calculateBlockRootHash(block);
        blockNumber++;
        // set blocks roughly apart, so in latency related tests, streaming latency don't reduce drastically from
        // one block to the next
        recordItemBuilder.setNow(Utility.convertToInstant(blockTimestamp).plus(interval));

        return new BlockRecord(block);
    }

    private List<BlockItem> transactionUnit() {
        var recordItem = recordItemBuilder.cryptoTransfer().build();
        var signedTransaction = BlockItem.newBuilder()
                .setSignedTransaction(recordItem.getTransaction().getSignedTransactionBytes())
                .build();
        var transactionResult = BlockItem.newBuilder()
                .setTransactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(recordItem.getTransactionRecord().getConsensusTimestamp())
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .setTransferList(recordItem.getTransactionRecord().getTransferList())
                        .build())
                .build();
        // for simplicity, no state changes / trace data
        return List.of(signedTransaction, transactionResult);
    }

    public record BlockRecord(BlockItemSet block, AtomicLong latency, AtomicLong readyTime) {
        public BlockRecord(BlockItemSet block) {
            this(block, new AtomicLong(0), new AtomicLong(0));
        }
    }

    private static BlockItem blockFooter(final byte[] previousBlockRootHash) {
        return BlockItem.newBuilder()
                .setBlockFooter(BlockFooter.newBuilder()
                        .setPreviousBlockRootHash(fromBytes(previousBlockRootHash))
                        // for simplicity, set both to all zero hash
                        .setRootHashOfAllBlockHashesTree(fromBytes(ALL_ZERO_HASH))
                        .setStartOfBlockStateRootHash(fromBytes(ALL_ZERO_HASH)))
                .build();
    }

    private static BlockItem blockProof(final long blockNumber) {
        return BlockItem.newBuilder()
                .setBlockProof(BlockProof.newBuilder().setBlock(blockNumber))
                .build();
    }
}
