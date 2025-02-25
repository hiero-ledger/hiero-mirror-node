// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block;

import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.downloader.StreamFileTransformer;
import com.hedera.mirror.importer.downloader.block.transformer.BlockItemTransformerFactory;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.util.Version;

@Named
@RequiredArgsConstructor
public class BlockFileTransformer implements StreamFileTransformer<RecordFile, BlockFile> {

    private final BlockItemTransformerFactory blockItemTransformerFactory;

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

    private List<RecordItem> getRecordItems(Collection<BlockItem> blockItems, Version hapiVersion) {
        if (blockItems.isEmpty()) {
            return Collections.emptyList();
        }

        RecordItem previousItem = null;
        var recordItems = new ArrayList<RecordItem>(blockItems.size());
        for (var blockItem : blockItems) {
            var recordItem = RecordItem.builder()
                    .hapiVersion(hapiVersion)
                    .previous(previousItem)
                    .transaction(blockItem.transaction())
                    .transactionIndex(recordItems.size())
                    .transactionRecord(blockItemTransformerFactory.getTransactionRecord(blockItem))
                    .build();
            recordItems.add(recordItem);
            previousItem = recordItem;
        }

        return recordItems;
    }
}
