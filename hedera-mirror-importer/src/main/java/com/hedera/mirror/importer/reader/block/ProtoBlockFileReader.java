// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader.block;

import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_PROOF;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.EVENT_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.EVENT_TRANSACTION;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.RECORD_FILE;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.ROUND_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.STATE_CHANGES;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.TRANSACTION_OUTPUT;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.TRANSACTION_RESULT;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.protoc.Block;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
import com.hederahashgraph.api.proto.java.BlockHashAlgorithm;
import com.hederahashgraph.api.proto.java.Transaction;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Named
public class ProtoBlockFileReader implements BlockFileReader {

    static final int VERSION = 7;

    @Override
    public BlockFile read(StreamFileData streamFileData) {
        String filename = streamFileData.getFilename();

        try (var inputStream = streamFileData.getInputStream()) {
            var block = Block.parseFrom(inputStream);
            var context = new ReaderContext(block.getItemsList(), filename);
            var blockFileBuilder = context.getBlockFile()
                    .loadStart(streamFileData.getStreamFilename().getTimestamp())
                    .name(filename)
                    .version(VERSION);

            var blockItem = context.readBlockItemFor(RECORD_FILE);
            if (blockItem != null) {
                return blockFileBuilder
                        .recordFileItem(blockItem.getRecordFile())
                        .build();
            }

            readBlockHeader(context);
            readRounds(context);
            readStandaloneStateChanges(context);
            readBlockProof(context);

            var blockFile = blockFileBuilder.build();
            var bytes = streamFileData.getBytes();
            var items = blockFile.getItems();
            blockFile.setBytes(bytes);
            blockFile.setCount((long) items.size());
            blockFile.setHash(context.getBlockRootHashDigest().digest());
            blockFile.setSize(bytes.length);

            if (!items.isEmpty()) {
                blockFile.setConsensusStart(items.getFirst().getConsensusTimestamp());
                blockFile.setConsensusEnd(items.getLast().getConsensusTimestamp());
            } else {
                blockFile.setConsensusStart(context.getLastMetaTimestamp());
                blockFile.setConsensusEnd(context.getLastMetaTimestamp());
            }

            return blockFile;
        } catch (InvalidStreamFileException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidStreamFileException("Failed to read " + filename, e);
        }
    }

    private void readBlockHeader(ReaderContext context) {
        var blockItem = context.readBlockItemFor(BLOCK_HEADER);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block header in block file " + context.getFilename());
        }

        var blockFileBuilder = context.getBlockFile();
        var blockHeader = blockItem.getBlockHeader();

        if (blockHeader.getHashAlgorithm().equals(BlockHashAlgorithm.SHA2_384)) {
            blockFileBuilder.digestAlgorithm(DigestAlgorithm.SHA_384);
        } else {
            throw new InvalidStreamFileException(String.format(
                    "Unsupported hash algorithm %s in block header of block file %s",
                    blockHeader.getHashAlgorithm(), context.getFilename()));
        }

        blockFileBuilder.blockHeader(blockHeader);
        blockFileBuilder.index(blockHeader.getNumber());
    }

    private void readBlockProof(ReaderContext context) {
        var blockItem = context.readBlockItemFor(BLOCK_PROOF);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block proof in file " + context.getFilename());
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
            readEventTransactions(context);
        }
    }

    private void readEventTransactions(ReaderContext context) {
        BlockItem protoBlockItem;
        Transaction transaction;
        try {
            while ((transaction = context.getApplicationTransaction()) != null) {
                var transactionResultProtoBlockItem = context.readBlockItemFor(TRANSACTION_RESULT);
                if (transactionResultProtoBlockItem == null) {
                    throw new InvalidStreamFileException(
                            "Missing transaction result in block file " + context.getFilename());
                }

                var transactionOutputs = new EnumMap<TransactionCase, TransactionOutput>(TransactionCase.class);
                while ((protoBlockItem = context.readBlockItemFor(TRANSACTION_OUTPUT)) != null) {
                    var transactionOutput = protoBlockItem.getTransactionOutput();
                    transactionOutputs.put(transactionOutput.getTransactionCase(), transactionOutput);
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

                var blockItem = com.hedera.mirror.common.domain.transaction.BlockItem.builder()
                        .previous(context.getLastBlockItem())
                        .stateChanges(Collections.unmodifiableList(stateChangesList))
                        .transaction(transaction)
                        .transactionResult(transactionResult)
                        .transactionOutputs(Collections.unmodifiableMap(transactionOutputs))
                        .build();
                context.getBlockFile().item(blockItem);
                context.setLastBlockItem(blockItem);
            }
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidStreamFileException(
                    "Failed to deserialize Transaction from block file " + context.getFilename(), e);
        }
    }

    private void readRounds(ReaderContext context) {
        BlockItem blockItem;
        while ((blockItem = context.readBlockItemFor(ROUND_HEADER)) != null) {
            context.getBlockFile().onNewRound(blockItem.getRoundHeader().getRoundNumber());
            readStandaloneStateChanges(context);
            readEvents(context);
        }
    }

    /**
     * Read standalone state changes. There are two types of such state changes: one that only appears in a network's
     * genesis block, between the first round header and the first event header; one that always appears before the
     * block proof
     *
     * @param context - The reader context
     */
    private void readStandaloneStateChanges(ReaderContext context) {
        BlockItem blockItem;
        while ((blockItem = context.readBlockItemFor(STATE_CHANGES)) != null) {
            // read all standalone statechanges
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
        private com.hedera.mirror.common.domain.transaction.BlockItem lastBlockItem;

        @NonFinal
        @Setter
        private Long lastMetaTimestamp; // The last consensus timestamp from metadata

        ReaderContext(@NotNull List<BlockItem> blockItems, @NotNull String filename) {
            this.blockFile = BlockFile.builder();
            this.blockItems = blockItems;
            this.blockRootHashDigest = new BlockRootHashDigest();
            this.filename = filename;
        }

        public void setLastBlockItem(com.hedera.mirror.common.domain.transaction.BlockItem lastBlockItem) {
            this.lastBlockItem = lastBlockItem;
            if (lastBlockItem != null && lastBlockItem.getTransactionBody().hasAtomicBatch()) {
                this.batchIndex = 0;
                this.batchBody = lastBlockItem.getTransactionBody().getAtomicBatch();
            }
        }

        public Transaction getApplicationTransaction() throws InvalidProtocolBufferException {
            var blockItemProto = readBlockItemFor(EVENT_TRANSACTION);

            if (blockItemProto != null && blockItemProto.hasEventTransaction()) {
                return Transaction.parseFrom(
                        blockItemProto.getEventTransaction().getApplicationTransaction());
            }

            if (batchBody != null && batchIndex < batchBody.getTransactionsCount()) {
                var innerTransaction = Transaction.parseFrom(batchBody.getTransactions(batchIndex++));
                if (innerTransaction == null || Transaction.getDefaultInstance().equals(innerTransaction)) {
                    throw new InvalidStreamFileException(
                            "Failed to parse inner transaction from atomic batch in block file " + filename);
                }
                return innerTransaction;
            }

            return null;
        }

        /**
         * Returns the current block item if it matches the itemCase, and advances the index. If no match, index is not
         * changed
         * @param itemCase - block item case
         * @return The matching block item, or null
         */
        public BlockItem readBlockItemFor(ItemCase itemCase) {
            if (index >= blockItems.size()) {
                return null;
            }

            var blockItem = blockItems.get(index);
            if (blockItem.getItemCase() != itemCase) {
                return null;
            }

            index++;
            switch (itemCase) {
                case EVENT_HEADER, EVENT_TRANSACTION, ROUND_HEADER -> blockRootHashDigest.addInputBlockItem(blockItem);
                case BLOCK_HEADER, STATE_CHANGES, TRANSACTION_OUTPUT, TRANSACTION_RESULT ->
                    blockRootHashDigest.addOutputBlockItem(blockItem);
                default -> {
                    // other block items aren't considered input / output
                }
            }

            return blockItem;
        }
    }
}
