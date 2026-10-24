// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.BLOCK_FOOTER;
import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.BLOCK_HEADER;
import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.BLOCK_PROOF;
import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.EVENT_HEADER;
import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.RECORD_FILE;
import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.ROUND_HEADER;
import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.SIGNED_TRANSACTION;
import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.STATE_CHANGES;
import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.TRACE_DATA;
import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.TRANSACTION_OUTPUT;
import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.TRANSACTION_RESULT;
import static org.hiero.mirror.common.domain.transaction.RecordFile.GENESIS_BLOCK_NUMBER;
import static org.hiero.mirror.common.util.DomainUtils.bytesToHex;
import static org.hiero.mirror.common.util.DomainUtils.parseProtobuf;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockFooter;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import com.hedera.hapi.block.stream.trace.protoc.TraceData;
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.codec.binary.Hex;
import org.hiero.block.internal.protoc.BlockItemUnparsed;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.reader.block.hash.BlockRootHashDigest;
import org.hiero.mirror.importer.reader.block.record.RecordFileItemReader;
import org.jspecify.annotations.Nullable;
import org.springframework.util.function.ThrowingFunction;

@CustomLog
@Named
@RequiredArgsConstructor
public final class BlockStreamReaderImpl implements BlockStreamReader {

    private final InitialStateReader initialStateReader;
    private final RecordFileItemReader recordFileItemReader;

    @Override
    public BlockFile read(final BlockStream blockStream) {
        final var context = new ReaderContext(blockStream.blockItems(), blockStream.filename());
        final byte[] bytes = blockStream.bytes();
        final var blockFileBuilder = context.getBlockFile()
                .bytes(bytes)
                .loadStart(blockStream.loadStart())
                .name(blockStream.filename())
                .size(blockStream.size())
                .version(VERSION);

        final var blockHeader = readBlockHeader(context);
        final var recordFileItem = context.readBlockItemFor(RECORD_FILE);
        if (recordFileItem == null) {
            readRounds(context);
        }

        final var blockFooter = readBlockFooter(context);
        readBlockProof(context);

        final byte[] rootHash = context.getBlockRootHashDigest().digest(blockHeader.getBlockTimestamp(), blockFooter);
        final var blockFile = blockFileBuilder
                .hash(Hex.encodeHexString(rootHash))
                .rawHash(rootHash)
                .build();

        if (recordFileItem == null) {
            final var items = blockFile.getItems();
            blockFile.setCount((long) items.size());

            if (!items.isEmpty()) {
                final var bounds = context.getConsensusTimestampTracker()
                        .validateItemOrder(
                                blockFile.getName(),
                                items.getFirst().getConsensusTimestamp(),
                                items.getLast().getConsensusTimestamp());

                blockFile.setConsensusStart(bounds.start());
                blockFile.setConsensusEnd(bounds.end());
            } else {
                final long blockTimestamp = DomainUtils.timestampInNanosMax(
                        blockFile.getBlockHeader().getBlockTimestamp());
                blockFile.setConsensusStart(blockTimestamp);
                blockFile.setConsensusEnd(blockTimestamp);
            }
        } else {
            final int recordFileVersion =
                    blockFile.getBlockProof().getSignedRecordFileProof().getVersion();
            final var recordFile = recordFileItemReader.read(
                    parseItem(recordFileItem.getRecordFile(), RecordFileItem::parseFrom), recordFileVersion);
            blockFile.setRecordFile(recordFile);
            recordFile.setLoadStart(blockStream.loadStart());
            recordFile.setPreviousWrappedRecordBlockHash(blockFile.getRawPreviousHash());
            recordFile.setWrappedRecordBlockHash(rootHash);

            readInitialState(context, recordFile);
        }

        if (rootHash.length == DigestAlgorithm.SHA_256.getSize()) {
            blockFile.setDigestAlgorithm(DigestAlgorithm.SHA_256);
        } else if (rootHash.length == DigestAlgorithm.SHA_384.getSize()) {
            blockFile.setDigestAlgorithm(DigestAlgorithm.SHA_384);
        } else {
            log.warn(
                    "Unsupported root hash size {} in block {}, default to SHA-256",
                    rootHash.length,
                    blockFile.getName());
            blockFile.setDigestAlgorithm(DigestAlgorithm.SHA_256);
        }

        return blockFile;
    }

    // The block items are already in memory, and the block's size has been limited when reading the block
    private static <T> T parseItem(final ByteString item, final ThrowingFunction<CodedInputStream, T> parser) {
        return parseProtobuf(item, parser, Integer.MAX_VALUE);
    }

    private BlockFooter readBlockFooter(final ReaderContext context) {
        final var blockItem = context.readBlockItemFor(BLOCK_FOOTER);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block footer in block " + context.getFilename());
        }

        final var blockFooter = parseItem(blockItem.getBlockFooter(), BlockFooter::parseFrom);
        final byte[] previousHash = toBytes(blockFooter.getPreviousBlockRootHash());
        context.getBlockFile().previousHash(bytesToHex(previousHash)).rawPreviousHash(previousHash);
        return blockFooter;
    }

    private BlockHeader readBlockHeader(final ReaderContext context) {
        final var blockItem = context.readBlockItemFor(BLOCK_HEADER);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block header in block " + context.getFilename());
        }

        final var blockFileBuilder = context.getBlockFile();
        final var blockHeader = parseItem(blockItem.getBlockHeader(), BlockHeader::parseFrom);
        blockFileBuilder.blockHeader(blockHeader);
        blockFileBuilder.index(blockHeader.getNumber());

        if (blockHeader.getNumber() == GENESIS_BLOCK_NUMBER) {
            context.collectStateChanges();
        }

        return blockHeader;
    }

    private void readBlockProof(final ReaderContext context) {
        final var blockItem = context.readBlockItemFor(BLOCK_PROOF);
        if (blockItem == null) {
            throw new InvalidStreamFileException("Missing block proof in block " + context.getFilename());
        }

        final var blockProof = parseItem(blockItem.getBlockProof(), BlockProof::parseFrom);
        context.getBlockFile().blockProof(blockProof);

        // Read remaining blockProof block items. In a later release, implement support of multiple blockProof items,
        // primarily for wrapped record files which come with both SignedRecordFileProof and StateProof
        while (context.readBlockItemFor(BLOCK_PROOF) != null) {
            log.debug("Skip remaining block proof block items");
        }
    }

    private void readEvents(final ReaderContext context) {
        boolean advanced;
        do {
            final int lastIndex = context.getIndex();
            context.readBlockItemFor(EVENT_HEADER);
            readSignedTransactions(context);
            advanced = context.getIndex() != lastIndex;
        } while (advanced);
    }

    private void readInitialState(final ReaderContext context, final RecordFile recordFile) {
        final var stateChangesItems = context.getStateChangesList();
        if (recordFile.getIndex() != GENESIS_BLOCK_NUMBER || stateChangesItems == null) {
            return;
        }

        final var stateChangesList = new ArrayList<StateChanges>(stateChangesItems.size());
        for (final var stateChanges : stateChangesItems) {
            stateChangesList.add(parseItem(stateChanges, StateChanges::parseFrom));
        }

        recordFile.setInitialState(initialStateReader.read(stateChangesList));
    }

    private void readSignedTransactions(final ReaderContext context) {
        BlockItemUnparsed protoBlockItem;
        SignedTransactionInfo signedTransactionInfo;

        while ((signedTransactionInfo = context.getSignedTransaction()) != null) {
            final var signedTransaction =
                    parseProtobuf(signedTransactionInfo.signedTransaction(), SignedTransaction::parseFrom);
            final var transactionBody = parseProtobuf(signedTransaction.getBodyBytes(), TransactionBody::parseFrom);
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
                final var transactionOutput =
                        parseItem(protoBlockItem.getTransactionOutput(), TransactionOutput::parseFrom);
                transactionOutputs.put(transactionOutput.getTransactionCase(), transactionOutput);
            }

            final var traceDataList = new ArrayList<TraceData>();
            while ((protoBlockItem = context.readBlockItemFor(TRACE_DATA)) != null) {
                traceDataList.add(parseItem(protoBlockItem.getTraceData(), TraceData::parseFrom));
            }

            final var stateChangesList = new ArrayList<StateChanges>();
            final var transactionResult =
                    parseItem(transactionResultProtoBlockItem.getTransactionResult(), TransactionResult::parseFrom);
            while ((protoBlockItem = context.readBlockItemFor(STATE_CHANGES)) != null) {
                final var stateChanges = parseItem(protoBlockItem.getStateChanges(), StateChanges::parseFrom);
                if (!Objects.equals(transactionResult.getConsensusTimestamp(), stateChanges.getConsensusTimestamp())) {
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
            context.setLastBlockTransaction(blockTransaction, signedTransactionInfo.userTransactionInBatch());

            final var blockFileBuilder = context.getBlockFile();
            blockFileBuilder.item(blockTransaction);
            context.getConsensusTimestampTracker().track(blockTransaction.getConsensusTimestamp());
            if (blockTransaction.getTransactionBody().hasLedgerIdPublication() && blockTransaction.isSuccessful()) {
                blockFileBuilder.lastLedgerIdPublicationTransaction(blockTransaction);
            }
        }
    }

    private void readRounds(ReaderContext context) {
        BlockItemUnparsed blockItem;
        while ((blockItem = context.readBlockItemFor(ROUND_HEADER)) != null) {
            final var roundHeader = parseItem(blockItem.getRoundHeader(), RoundHeader::parseFrom);
            context.getBlockFile().onNewRound(roundHeader.getRoundNumber());
            readEvents(context);
        }
    }

    @Value
    private static class ReaderContext {

        private BlockFile.BlockFileBuilder blockFile;
        private List<BlockItemUnparsed> blockItems;
        private BlockRootHashDigest blockRootHashDigest;
        private ConsensusTimestampTracker consensusTimestampTracker = new ConsensusTimestampTracker();
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
        private BlockTransaction lastChildTransaction;

        // The serialized state changes in the genesis block, parsed only when the initial state is read
        @NonFinal
        @Nullable
        private List<ByteString> stateChangesList;

        ReaderContext(final List<BlockItemUnparsed> blockItems, final String filename) {
            this.blockFile = BlockFile.builder();
            this.blockItems = blockItems;
            this.blockRootHashDigest = new BlockRootHashDigest();
            this.filename = filename;
        }

        void collectStateChanges() {
            stateChangesList = new ArrayList<>();
        }

        @Nullable
        SignedTransactionInfo getSignedTransaction() {
            final var blockItemProto = readBlockItemFor(SIGNED_TRANSACTION);
            if (blockItemProto != null) {
                return new SignedTransactionInfo(toBytes(blockItemProto.getSignedTransaction()), false);
            }

            if (batchBody != null && batchIndex < batchBody.getTransactionsCount()) {
                return new SignedTransactionInfo(toBytes(batchBody.getTransactions(batchIndex++)), true);
            }

            return null;
        }

        /**
         * Returns the current block item if it matches the itemCase, and advances the index. Index can also advance
         * until consecutive non-transaction statechanges block items are read
         *
         * @param itemCase - block item case
         * @return The matching block item, or null
         */
        @Nullable
        BlockItemUnparsed readBlockItemFor(final BlockItemUnparsed.ItemCase itemCase) {
            while (index < blockItems.size()) {
                final var blockItem = blockItems.get(index);
                final var currentItemCase = blockItem.getItemCase();
                if (currentItemCase == itemCase) {
                    consumeBlockItem(blockItem);
                    return blockItem;
                } else if (shouldSkip(currentItemCase, itemCase)) {
                    consumeBlockItem(blockItem);
                } else {
                    return null;
                }
            }

            return null;
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

            // Link child transactions (e.g., hook executions) that share the same parent for intermediate
            // contract storage changes. This uses the nextSibling chain to enable storage resolution
            // for hook execution child transactions triggered by a parent transaction (e.g., crypto transfer)
            if (lastChildTransaction != null
                    && lastBlockTransaction.getParentConsensusTimestamp() != null
                    && lastBlockTransaction
                            .getParentConsensusTimestamp()
                            .equals(lastChildTransaction.getParentConsensusTimestamp())) {
                lastChildTransaction.setNextSibling(lastBlockTransaction);
            }

            // Track the last child transaction for linking siblings with the same parent
            if (lastBlockTransaction.getParentConsensusTimestamp() != null) {
                lastChildTransaction = lastBlockTransaction;
            } else {
                // Reset when we encounter a non-child transaction
                lastChildTransaction = null;
            }

            this.lastBlockTransaction = lastBlockTransaction;
            if (lastBlockTransaction.getTransactionBody().hasAtomicBatch()) {
                this.batchIndex = 0;
                this.batchBody = lastBlockTransaction.getTransactionBody().getAtomicBatch();
                this.lastUserTransactionInBatch = null;
            }
        }

        private static boolean shouldSkip(
                final BlockItemUnparsed.ItemCase actual, final BlockItemUnparsed.ItemCase expected) {
            // Skip a statechanges block item when the expected type is not statechanges / trace data / transaction
            // output. Such a statechanges block item must be a non-transaction statechanges block item. When
            // the expected type is either trace data or transaction output, the statechanges block item should not be
            // skipped since it may belong to the current signed transaction
            return actual != expected
                    && actual == STATE_CHANGES
                    && expected != TRACE_DATA
                    && expected != TRANSACTION_OUTPUT;
        }

        private void consumeBlockItem(final BlockItemUnparsed blockItem) {
            blockRootHashDigest.addBlockItem(blockItem);
            index++;

            if (blockItem.hasStateChanges() && stateChangesList != null) {
                stateChangesList.add(blockItem.getStateChanges());
            }
        }
    }

    private record SignedTransactionInfo(byte[] signedTransaction, boolean userTransactionInBatch) {}
}
