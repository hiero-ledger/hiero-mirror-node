// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.downloader.StreamFileTransformer;
import org.hiero.mirror.importer.downloader.block.transformer.BlockTransactionTransformerFactory;
import org.springframework.data.util.Version;

@Named
@RequiredArgsConstructor
public class BlockFileTransformer implements StreamFileTransformer<RecordFile, BlockFile> {

    private final BlockTransactionTransformerFactory blockTransactionTransformerFactory;

    @Override
    public RecordFile transform(BlockFile blockFile) {
        var blockHeader = blockFile.getBlockHeader();
        var hapiProtoVersion = blockHeader.getHapiProtoVersion();
        int major = hapiProtoVersion.getMajor();
        int minor = hapiProtoVersion.getMinor();
        int patch = hapiProtoVersion.getPatch();
        var hapiVersion = new Version(major, minor, patch);
        var softwareVersion = blockHeader.getSoftwareVersion();
        return RecordFile.builder()
                .bytes(blockFile.getBytes())
                .consensusEnd(blockFile.getConsensusEnd())
                .consensusStart(blockFile.getConsensusStart())
                .count(blockFile.getCount())
                .digestAlgorithm(blockFile.getDigestAlgorithm())
                .fileHash(StringUtils.EMPTY)
                .hapiVersionMajor(major)
                .hapiVersionMinor(minor)
                .hapiVersionPatch(patch)
                .hash(blockFile.getHash())
                .index(blockFile.getIndex())
                .items(getRecordItems(blockFile.getItems(), hapiVersion))
                .loadEnd(blockFile.getLoadEnd())
                .loadStart(blockFile.getLoadStart())
                .name(blockFile.getName())
                .nodeId(blockFile.getNodeId())
                .previousHash(blockFile.getPreviousHash())
                .roundEnd(blockFile.getRoundEnd())
                .roundStart(blockFile.getRoundStart())
                .size(blockFile.getSize())
                .softwareVersionMajor(softwareVersion.getMajor())
                .softwareVersionMinor(softwareVersion.getMinor())
                .softwareVersionPatch(softwareVersion.getPatch())
                .version(blockFile.getVersion())
                .build();
    }

    private List<RecordItem> getRecordItems(List<BlockTransaction> blockTransactions, Version hapiVersion) {
        if (blockTransactions.isEmpty()) {
            return Collections.emptyList();
        }

        // Transform block items in reverse order. This solves the problem of inferring correct intermediate state
        // changes for child transactions, notably, the majority should be a parent contract call transaction with
        // multiple child transactions. For such transactions, state changes are only committed for hence written to
        // the parent transaction. The state changes are aggregated final changes due to the execution of such
        // transactions as a whole. As a result, intermediate state changes are lost, e.g., child transactions which
        // change a token's supply.
        // Some scenarios that the reverse order helps
        // - token total supply. The state changes has the final total supply of applying all changes, reverse
        //   processing allows applying the delta of an applicable transaction to get the correct token total supply
        //   for the preceding
        // - newly created entities. For example, the child transactions can create many topics, and also delete many.
        //   It's hard to figure out which transaction creates which topic since a consensus delete topic transaction
        //   can delete either a pre-existing topic or a new topic. With the invariance that a consensus create topic
        //   transaction reaching consensus later should always get a larger topic id, when processing child consensus
        //   create topic transactions in reverse order, the topic created by such a transaction is always the largest
        //   unclaimed one
        var builders = new ArrayList<RecordItem.RecordItemBuilder>(blockTransactions.size());
        for (int index = blockTransactions.size() - 1; index >= 0; index--) {
            var blockTransaction = blockTransactions.get(index);
            var builder = RecordItem.builder()
                    .hapiVersion(hapiVersion)
                    .signatureMap(blockTransaction.getSignedTransaction().getSigMap())
                    .transaction(blockTransaction.getTransaction())
                    .transactionBody(blockTransaction.getTransactionBody())
                    .transactionIndex(index);
            blockTransactionTransformerFactory.transform(blockTransaction, builder);
            builders.add(builder);
        }

        // An unpleasant performance degradation of reverse order is the second pass to build the record items, just to
        // set the previous link
        var recordItems = new ArrayList<RecordItem>(blockTransactions.size());
        RecordItem previousItem = null;
        for (int index = builders.size() - 1; index >= 0; index--) {
            var builder = builders.get(index);
            var recordItem = builder.previous(previousItem).build();
            recordItems.add(recordItem);
            previousItem = recordItem;
        }

        return Collections.unmodifiableList(recordItems);
    }
}
