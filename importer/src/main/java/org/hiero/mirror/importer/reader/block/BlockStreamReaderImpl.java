// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_FOOTER;
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
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.trace.protoc.TraceData;
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
import com.hederahashgraph.api.proto.java.BlockHashAlgorithm;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import lombok.CustomLog;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@CustomLog
@Named
@NullMarked
public final class BlockStreamReaderImpl implements BlockStreamReader {

    @Override
    public BlockFile read(final BlockStream blockStream) {
        final var context = new ReaderContext(blockStream.blockItems(), blockStream.filename());
        final byte[] bytes = blockStream.bytes();
        final Integer size = bytes != null ? bytes.length : null;
        final var blockFileBuilder = context.getBlockFile()
                .bytes(bytes)
                .loadStart(blockStream.loadStart())
                .name(blockStream.filename())
                .nodeId(blockStream.nodeId())
                .size(size)
                .version(VERSION);

        final var blockItem = context.readBlockItemFor(RECORD_FILE);
        if (blockItem != null) {
            return blockFileBuilder.recordFileItem(blockItem.getRecordFile()).build();
        }

        readBlockHeader(context);
        readRounds(context);
        readNonTransactionStateChanges(context);
        readBlockFooter(context);
        readBlockProof(context);

        final var blockFile = blockFileBuilder.build();
        final var items = blockFile.getItems();
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

    private void readBlockFooter(final ReaderContext context) {
        final var blockItem = context.readBlockItemFor(BLOCK_FOOTER);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block footer in block " + context.getFilename());
        }

        final var blockFooter = blockItem.getBlockFooter();
        context.getBlockFile()
                .previousHash(DomainUtils.bytesToHex(DomainUtils.toBytes(blockFooter.getPreviousBlockRootHash())));
    }

    private void readBlockHeader(final ReaderContext context) {
        final var blockItem = context.readBlockItemFor(BLOCK_HEADER);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block header in block " + context.getFilename());
        }

        final var blockFileBuilder = context.getBlockFile();
        final var blockHeader = blockItem.getBlockHeader();

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

    private void readBlockProof(final ReaderContext context) {
        final var blockItem = context.readBlockItemFor(BLOCK_PROOF);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block proof in block " + context.getFilename());
        }

        context.getBlockFile().blockProof(blockItem.getBlockProof());

        // Read remaining blockProof block items. In a later release, implement new block & state merkle tree support
        while (context.readBlockItemFor(BLOCK_PROOF) != null) {
            log.debug("Skip remaining block proof block items");
        }
    }

    private void readEvents(final ReaderContext context) {
        while (context.readBlockItemFor(EVENT_HEADER) != null) {
            readSignedTransactions(context);
        }
    }

    private void readSignedTransactions(final ReaderContext context) {
        BlockItem protoBlockItem;
        SignedTransactionInfo signedTransactionInfo;
        try {
            while ((signedTransactionInfo = context.getSignedTransaction()) != null) {
                final var signedTransaction = SignedTransaction.parseFrom(signedTransactionInfo.signedTransaction());
                final var transactionBody = TransactionBody.parseFrom(signedTransaction.getBodyBytes());
                final var transactionResultProtoBlockItem = context.readBlockItemFor(TRANSACTION_RESULT);
                if (transactionResultProtoBlockItem == null) {
                    if (signedTransactionInfo.userTransactionInBatch()) {
                        // #12313 - when a user transaction in an atomic batch fails, any subsequent user transaction
                        // in the same batch will not execute thus won't have a TransactionResult block item
                        context.resetBatchTransaction();
                    }

                    // System transactions won't have transactionResult either, continue to next block item
                    continue;
                }

                final var transactionOutputs = new EnumMap<TransactionCase, TransactionOutput>(TransactionCase.class);
                while ((protoBlockItem = context.readBlockItemFor(TRANSACTION_OUTPUT)) != null) {
                    final var transactionOutput = protoBlockItem.getTransactionOutput();
                    transactionOutputs.put(transactionOutput.getTransactionCase(), transactionOutput);
                }

                final var traceDataList = new ArrayList<TraceData>();
                while ((protoBlockItem = context.readBlockItemFor(TRACE_DATA)) != null) {
                    traceDataList.add(protoBlockItem.getTraceData());
                }

                final var stateChangesList = new ArrayList<StateChanges>();
                final var transactionResult = transactionResultProtoBlockItem.getTransactionResult();
                while ((protoBlockItem = context.readBlockItemFor(STATE_CHANGES)) != null) {
                    final var stateChanges = protoBlockItem.getStateChanges();
                    if (!Objects.equals(
                            transactionResult.getConsensusTimestamp(), stateChanges.getConsensusTimestamp())) {
                        context.setLastMetaTimestamp(
                                DomainUtils.timestampInNanosMax(stateChanges.getConsensusTimestamp()));
                        break;
                    }

                    stateChangesList.add(stateChanges);
                }

                final var blockTransaction = BlockTransaction.builder()
                        .previous(context.getLastBlockTransaction())
                        .signedTransaction(signedTransaction)
                        .signedTransactionBytes(signedTransactionInfo.signedTransaction())
                        .stateChanges(Collections.unmodifiableList(stateChangesList))
                        .traceData(Collections.unmodifiableList(traceDataList))
                        .transactionBody(transactionBody)
                        .transactionResult(transactionResult)
                        .transactionOutputs(Collections.unmodifiableMap(transactionOutputs))
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
        @Nullable
        private AtomicBatchTransactionBody batchBody;

        @NonFinal
        private int index;

        @NonFinal
        @Nullable
        private BlockTransaction lastBlockTransaction;

        @NonFinal
        @Nullable
        private BlockTransaction lastUserTransactionInBatch;

        @NonFinal
        @Nullable
        @Setter
        private Long lastMetaTimestamp; // The last consensus timestamp from metadata

        ReaderContext(final List<BlockItem> blockItems, final String filename) {
            this.blockFile = BlockFile.builder();
            this.blockItems = blockItems;
            this.blockRootHashDigest = new BlockRootHashDigest();
            this.filename = filename;
        }

        @Nullable
        SignedTransactionInfo getSignedTransaction() {
            final var blockItemProto = readBlockItemFor(SIGNED_TRANSACTION);
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
        @Nullable
        BlockItem readBlockItemFor(final BlockItem.ItemCase itemCase) {
            if (index >= blockItems.size()) {
                return null;
            }

            final var blockItem = blockItems.get(index);
            if (blockItem.getItemCase() != itemCase) {
                return null;
            }

            blockRootHashDigest.addBlockItem(blockItem);
            index++;
            return blockItem;
        }

        void resetBatchTransaction() {
            batchBody = null;
            batchIndex = 0;
        }

        void setLastBlockTransaction(
                final BlockTransaction lastBlockTransaction, final boolean userTransactionInBatch) {
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
        }
    }

    private record SignedTransactionInfo(byte[] signedTransaction, boolean userTransactionInBatch) {}
}
