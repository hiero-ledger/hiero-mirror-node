// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_PROOF;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.EVENT_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.RECORD_FILE;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.ROUND_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.SIGNED_TRANSACTION;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.STATE_CHANGES;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.TRACE_DATA;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.TRANSACTION_OUTPUT;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.TRANSACTION_RESULT;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.stream.output.protoc.CreateScheduleOutput;
import com.hedera.hapi.block.stream.output.protoc.SignScheduleOutput;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.trace.protoc.TraceData;
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
import com.hederahashgraph.api.proto.java.BlockHashAlgorithm;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.jspecify.annotations.NonNull;

@Named
public final class BlockStreamReaderImpl implements BlockStreamReader {

    @Override
    public BlockFile read(@NonNull BlockStream blockStream) {
        var context = new ReaderContext(blockStream.blockItems(), blockStream.filename());
        byte[] bytes = blockStream.bytes();
        Integer size = bytes != null ? bytes.length : null;
        var blockFileBuilder = context.getBlockFile()
                .bytes(bytes)
                .loadStart(blockStream.loadStart())
                .name(blockStream.filename())
                .nodeId(blockStream.nodeId())
                .size(size)
                .version(VERSION);

        var blockItem = context.readBlockItemFor(RECORD_FILE);
        if (blockItem != null) {
            return blockFileBuilder.recordFileItem(blockItem.getRecordFile()).build();
        }

        readBlockHeader(context);
        readRounds(context);
        readNonTransactionStateChanges(context);
        readBlockProof(context);

        var blockFile = blockFileBuilder.build();
        var items = blockFile.getItems();
        blockFile.setCount((long) items.size());
        blockFile.setHash(context.getBlockRootHashDigest().digest());

        if (!items.isEmpty()) {
            blockFile.setConsensusStart(items.getFirst().getConsensusTimestamp());
            blockFile.setConsensusEnd(items.getLast().getConsensusTimestamp());
        } else {
            blockFile.setConsensusStart(context.getLastMetaTimestamp());
            blockFile.setConsensusEnd(context.getLastMetaTimestamp());
        }

        return blockFile;
    }

    private void readBlockHeader(ReaderContext context) {
        var blockItem = context.readBlockItemFor(BLOCK_HEADER);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block header in block " + context.getFilename());
        }

        var blockFileBuilder = context.getBlockFile();
        var blockHeader = blockItem.getBlockHeader();

        if (blockHeader.getHashAlgorithm().equals(BlockHashAlgorithm.SHA2_384)) {
            blockFileBuilder.digestAlgorithm(DigestAlgorithm.SHA_384);
        } else {
            throw new InvalidStreamFileException(String.format(
                    "Unsupported hash algorithm %s in block header of block %s",
                    blockHeader.getHashAlgorithm(), context.getFilename()));
        }

        blockFileBuilder.blockHeader(blockHeader);
        blockFileBuilder.index(blockHeader.getNumber());
    }

    private void readBlockProof(ReaderContext context) {
        var blockItem = context.readBlockItemFor(BLOCK_PROOF);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block proof in block " + context.getFilename());
        }

        var blockFile = context.getBlockFile();
        var blockProof = blockItem.getBlockProof();
        var blockRootHashDigest = context.getBlockRootHashDigest();
        byte[] previousHash = DomainUtils.toBytes(blockProof.getPreviousBlockRootHash());
        blockFile.blockProof(blockProof).previousHash(DomainUtils.bytesToHex(previousHash));
        blockRootHashDigest.setPreviousHash(previousHash);
        blockRootHashDigest.setStartOfBlockStateHash(DomainUtils.toBytes(blockProof.getStartOfBlockStateRootHash()));
    }

    private void readEvents(ReaderContext context) {
        while (context.readBlockItemFor(EVENT_HEADER) != null) {
            readSignedTransactions(context);
        }
    }

    private void readSignedTransactions(ReaderContext context) {
        BlockItem protoBlockItem;
        SignedTransactionInfo signedTransactionInfo;
        try {
            while ((signedTransactionInfo = context.getSignedTransaction()) != null) {
                var signedTransaction = SignedTransaction.parseFrom(signedTransactionInfo.signedTransaction());
                var transactionBody = TransactionBody.parseFrom(signedTransaction.getBodyBytes());
                var transactionResultProtoBlockItem = context.readBlockItemFor(TRANSACTION_RESULT);
                if (transactionResultProtoBlockItem == null) {
                    throw new InvalidStreamFileException(
                            "Missing transaction result in block " + context.getFilename());
                }

                var transactionOutputs = new EnumMap<TransactionCase, TransactionOutput>(TransactionCase.class);
                while ((protoBlockItem = context.readBlockItemFor(TRANSACTION_OUTPUT)) != null) {
                    var transactionOutput = protoBlockItem.getTransactionOutput();
                    transactionOutputs.put(transactionOutput.getTransactionCase(), transactionOutput);
                }

                var traceDataList = new ArrayList<TraceData>();
                while ((protoBlockItem = context.readBlockItemFor(TRACE_DATA)) != null) {
                    traceDataList.add(protoBlockItem.getTraceData());
                }

                var stateChangesList = new ArrayList<StateChanges>();
                var transactionResult = transactionResultProtoBlockItem.getTransactionResult();
                while ((protoBlockItem = context.readBlockItemFor(STATE_CHANGES)) != null) {
                    var stateChanges = protoBlockItem.getStateChanges();
                    if (!Objects.equals(
                            transactionResult.getConsensusTimestamp(), stateChanges.getConsensusTimestamp())) {
                        context.setLastMetaTimestamp(
                                DomainUtils.timestampInNanosMax(stateChanges.getConsensusTimestamp()));
                        break;
                    }

                    stateChangesList.add(stateChanges);
                }

                var blockTransaction = BlockTransaction.builder()
                        .previous(context.getLastBlockTransaction())
                        .signedTransaction(signedTransaction)
                        .signedTransactionBytes(signedTransactionInfo.signedTransaction())
                        .stateChanges(Collections.unmodifiableList(stateChangesList))
                        .traceData(Collections.unmodifiableList(traceDataList))
                        .transactionBody(transactionBody)
                        .transactionResult(transactionResult)
                        .transactionOutputs(Collections.unmodifiableMap(transactionOutputs))
                        .trigger(context.getScheduledTransactionTriggers().get(transactionBody.getTransactionID()))
                        .build();
                context.getBlockFile().item(blockTransaction);
                context.setLastBlockTransaction(blockTransaction, signedTransactionInfo.userTransactionInBatch());
            }
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidStreamFileException(
                    "Failed to deserialize Transaction from block " + context.getFilename(), e);
        }
    }

    private void readRounds(ReaderContext context) {
        BlockItem blockItem;
        while ((blockItem = context.readBlockItemFor(ROUND_HEADER)) != null) {
            context.getBlockFile().onNewRound(blockItem.getRoundHeader().getRoundNumber());
            readNonTransactionStateChanges(context);
            readEvents(context);
            readNonTransactionStateChanges(context);
        }
    }

    /**
     * Read non-transaction state changes. There are three possible places for such state changes
     * - in a network's genesis block, between the first round header and the first event header
     * - at the end of a round, right before the next round header
     * - before block proof
     *
     * @param context - The reader context
     */
    private void readNonTransactionStateChanges(ReaderContext context) {
        BlockItem blockItem;
        while ((blockItem = context.readBlockItemFor(STATE_CHANGES)) != null) {
            // read all non-transaction state changes
            context.setLastMetaTimestamp(
                    DomainUtils.timestampInNanosMax(blockItem.getStateChanges().getConsensusTimestamp()));
        }
    }

    @Value
    private static class ReaderContext {

        private BlockFile.BlockFileBuilder blockFile;
        private List<BlockItem> blockItems;
        private BlockRootHashDigest blockRootHashDigest;
        private String filename;

        @NonFinal
        private int batchIndex;

        @NonFinal
        private AtomicBatchTransactionBody batchBody;

        @NonFinal
        private int index;

        @NonFinal
        private BlockTransaction lastBlockTransaction;

        @NonFinal
        private BlockTransaction lastUserTransactionInBatch;

        @NonFinal
        @Setter
        private Long lastMetaTimestamp; // The last consensus timestamp from metadata

        private Map<TransactionID, BlockTransaction> scheduledTransactionTriggers = new HashMap<>();

        ReaderContext(@NonNull List<BlockItem> blockItems, @NonNull String filename) {
            this.blockFile = BlockFile.builder();
            this.blockItems = blockItems;
            this.blockRootHashDigest = new BlockRootHashDigest();
            this.filename = filename;
        }

        SignedTransactionInfo getSignedTransaction() {
            var blockItemProto = readBlockItemFor(SIGNED_TRANSACTION);
            if (blockItemProto != null) {
                return new SignedTransactionInfo(DomainUtils.toBytes(blockItemProto.getSignedTransaction()), false);
            }

            if (batchBody != null && batchIndex < batchBody.getTransactionsCount()) {
                return new SignedTransactionInfo(DomainUtils.toBytes(batchBody.getTransactions(batchIndex++)), true);
            }

            return null;
        }

        /**
         * Returns the current block item if it matches the itemCase, and advances the index. If no match, index is not
         * changed
         * @param itemCase - block item case
         * @return The matching block item, or null
         */
        BlockItem readBlockItemFor(BlockItem.ItemCase itemCase) {
            if (index >= blockItems.size()) {
                return null;
            }

            var blockItem = blockItems.get(index);
            if (blockItem.getItemCase() != itemCase) {
                return null;
            }

            index++;
            blockRootHashDigest.addBlockItem(blockItem);

            return blockItem;
        }

        void setLastBlockTransaction(@NonNull BlockTransaction lastBlockTransaction, boolean userTransactionInBatch) {
            if (userTransactionInBatch) {
                if (lastUserTransactionInBatch != null
                        && batchBody != null
                        && batchIndex <= batchBody.getTransactionsCount()) {
                    // link user transactions in a batch for intermediate contract storage changes. That is,
                    // given smart contract transactions X and Y in the same batch where X executes before Y, and
                    // a contract storage slot (C, K) written by both X and Y, the value written to the slot by X is the
                    // value read by Y, and the value written by Y is in the state changes externalized for the top
                    // level atomic batch transaction
                    lastUserTransactionInBatch.setNextInBatch(lastBlockTransaction);
                }
                lastUserTransactionInBatch = lastBlockTransaction;
            }

            this.lastBlockTransaction = lastBlockTransaction;
            if (lastBlockTransaction.getTransactionBody().hasAtomicBatch()) {
                this.batchIndex = 0;
                this.batchBody = lastBlockTransaction.getTransactionBody().getAtomicBatch();
                this.lastUserTransactionInBatch = null;
            }

            // A short-term scheduled transaction and its triggering transaction (either a schedule create or a schedule
            // sign) belong to one transactional unit, thus the statechanges are attached to the triggering transaction.
            // Build the map to link a short-term scheduled transaction to its trigger
            lastBlockTransaction
                    .getTransactionOutput(TransactionCase.CREATE_SCHEDULE)
                    .map(TransactionOutput::getCreateSchedule)
                    .filter(CreateScheduleOutput::hasScheduledTransactionId)
                    .map(CreateScheduleOutput::getScheduledTransactionId)
                    .or(() -> lastBlockTransaction
                            .getTransactionOutput(TransactionCase.SIGN_SCHEDULE)
                            .map(TransactionOutput::getSignSchedule)
                            .filter(SignScheduleOutput::hasScheduledTransactionId)
                            .map(SignScheduleOutput::getScheduledTransactionId))
                    .ifPresent(scheduledTransactionId ->
                            scheduledTransactionTriggers.put(scheduledTransactionId, lastBlockTransaction));
        }
    }

    private record SignedTransactionInfo(byte[] signedTransaction, boolean userTransactionInBatch) {}
}
