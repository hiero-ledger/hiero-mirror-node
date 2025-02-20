/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
            var builder = RecordItem.builder()
                    .hapiVersion(hapiVersion)
                    .previous(previousItem)
                    .transaction(blockItem.transaction())
                    .transactionIndex(recordItems.size());
            blockItemTransformerFactory.transform(blockItem, builder);
            var recordItem = builder.build();
            recordItems.add(recordItem);
            previousItem = recordItem;
        }

        return recordItems;
    }
}
