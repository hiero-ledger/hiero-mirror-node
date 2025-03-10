/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.downloader.record.RecordFileDownloader;
import com.hedera.mirror.importer.reader.block.BlockFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import io.micrometer.common.util.StringUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@CustomLog
@RequiredArgsConstructor
class BlockRecordCompareTest extends ImporterIntegrationTest {

    private final BlockFileTransformer blockFileTransformer;
    private final BlockFileReader blockFileReader;
    private final CommonDownloaderProperties commonDownloaderProperties;
    private final ConsensusNodeService consensusNodeService;
    private final StreamFileProvider streamFileProvider;
    private final RecordFileReader recordFileReader;
    private final RecordFileDownloader recordFileDownloader;

    @AfterEach
    void shutdown() {
        Thread.currentThread().interrupt();
    }

    @Test
    void compare() {
        var compareSet = new TreeMap<Long, BlockRecordSet>();

        long initialBlockNumber = 36118500; // testnet acceptance tests
        initialBlockNumber = 36394120;



        var consensusNode = consensusNodeService.getNodes().stream().filter(n -> n.getNodeId() == 0).findFirst().get();

        long blockNumber = initialBlockNumber;
        var streamFilename = StreamFilename.from(blockNumber);
        List<Integer> skippedTransactionTypes = List.of(TransactionType.ETHEREUMTRANSACTION.getProtoId(),
                TransactionType.CRYPTOCREATEACCOUNT.getProtoId() // Not available until 0.59
        );

        while(true) {
            int blocksToDownload = 1;
            while(blocksToDownload > 0) {
                BlockFile blockFile;
                try {
                    blockFile = downloadBlock(consensusNode, streamFilename);
                } catch (Exception e) {
                    // To account for gaps in blocks produced on testnet
                    log.error("Error downloading block file {}", streamFilename.getFilename());
                    blockNumber++;
                    streamFilename = StreamFilename.from(blockNumber);
                    continue;
                }

                if (blockFile.getConsensusStart() == null) {
                    blockNumber++;
                    streamFilename = StreamFilename.from(blockNumber);
                    continue;
                }

                var transformedRecordFile = blockFileTransformer.transform(blockFile);
                var transformedRecordItems = transformedRecordFile.getItems()
                        .stream()
                        .filter(r -> !r.getTransaction().getSignedTransactionBytes().isEmpty()) // Do not process block items with no signed transaction bytes - Ticket 10552
                        .filter(r -> !skippedTransactionTypes.contains(r.getTransactionType()))
                        .filter(r -> !(r.getTransactionRecord().getMemo().contains("Monitor pinger") ||
                                r.getTransactionRecord().getMemo().contains("hedera-mirror-monitor")))
                        .toList();
                if(transformedRecordItems.isEmpty()) {
                    blockNumber++;
                    streamFilename = StreamFilename.from(blockNumber);
                    continue;
                }

                transformedRecordItems.forEach(item -> compareSet.put(item.getConsensusTimestamp(), new BlockRecordSet(item, null)));
                blockNumber = blockFile.getBlockHeader().getNumber() + 1;
                streamFilename = StreamFilename.from(blockNumber);

                blocksToDownload--;
            }


            if(!compareSet.isEmpty()) {
                var firstConsensus = compareSet.firstKey();
                var lastConsensus = compareSet.lastKey();

                // Set timestamp back 4 seconds to make sure the range of transactions match up to that of the block
                var firstBlockInstant = Instant.ofEpochSecond(0, firstConsensus - 4000000000L);
                String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, firstBlockInstant);

                while(true) {
                    var recordFile = downloadRecordFile(consensusNode, filename);
                    if(recordFile.getConsensusEnd() < firstConsensus) {
                        filename = recordFile.getName();
                        continue;
                    }

                    var recordItems = recordFile.getItems().stream()
                            .filter(r -> !r.getTransaction().getSignedTransactionBytes().isEmpty()) // Do not process block items with no signed transaction bytes - Ticket 10552
                            .filter(r -> !skippedTransactionTypes.contains(r.getTransactionType()))
                            .filter(r -> !(r.getTransactionRecord().getMemo().contains("Monitor pinger") ||
                                    r.getTransactionRecord().getMemo().contains("hedera-mirror-monitor")))
                            .toList();
                    for(var item : recordItems) {
                        var blockRecordSet = compareSet.get(item.getConsensusTimestamp());
                        if(blockRecordSet != null) {
                            blockRecordSet.recordItem = item;
                        } else {
                            if(item.getConsensusTimestamp() >= firstConsensus && item.getConsensusTimestamp() <= lastConsensus) {
                                compareSet.put(item.getConsensusTimestamp(), new BlockRecordSet(null, item));
                            }
                        }
                    }

                    if(recordFile.getConsensusEnd() >= lastConsensus) {
                        break;
                    }

                    filename = recordFile.getName();
                }

                for (var key : compareSet.keySet()) {
                    var blockRecordItem = compareSet.get(key);
                    var recordRecordItem = blockRecordItem.recordItem;
                    var recordTransformedRecordItem = blockRecordItem.transformedRecordItem;

                    if(recordRecordItem == null) {
                        fail("Record item missing for block consensus timestamp {}", key);
                    }
                    // Temporary until contract transformers are implemented
                    if(recordRecordItem.getTransactionRecord().hasContractCallResult()) {
                        continue;
                    }

                    if(recordTransformedRecordItem == null) {
                        fail("Block record item missing for block consensus timestamp {}", key);
                    }

                    var memo = blockRecordItem.transformedRecordItem.getTransactionRecord().getMemo();
                    if(!StringUtils.isEmpty(memo)) {
                        log.info("Block Transaction memo: " + memo);
                    }

                    assertRecordItem(recordTransformedRecordItem, recordRecordItem);
                }

                compareSet.clear();
            }
        }
    }

    @AllArgsConstructor
    static class BlockRecordSet {
        public RecordItem transformedRecordItem;
        public RecordItem recordItem;
    }

    private BlockFile downloadBlock(ConsensusNode node, StreamFilename streamFilename) {
        commonDownloaderProperties.setPathType(PathType.NODE_ID);
        // Set to block specific path prefix
        commonDownloaderProperties.setPathPrefix("block-preview/testnet-date");
        var blockFileData = streamFileProvider
                .get(node, streamFilename)
                .blockOptional(commonDownloaderProperties.getTimeout())
                .orElseThrow();
        log.info("Downloaded block file {}", blockFileData.getFilename());
        return blockFileReader.read(blockFileData);
    }

    private RecordFile downloadRecordFile(ConsensusNode node, String previous) {
        commonDownloaderProperties.setPathType(PathType.ACCOUNT_ID);
        commonDownloaderProperties.setPathPrefix(null);
        var recordFileData = streamFileProvider
                .list(node, StreamFilename.from("recordstreams/record0.0.3/" + previous))
                .take(1).blockLast();
        log.info("Downloaded record file {}", recordFileData.getFilename());
        var recordFile = recordFileReader.read(recordFileData);
        recordFileDownloader.downloadSidecars(StreamFilename.from("recordstreams/record0.0.3/" + recordFileData.getFilename()), recordFile, node);
        return recordFile;
    }

    protected void assertRecordItem(RecordItem actual, RecordItem expected) {
        var ignoreFields = new ArrayList<>(Arrays.asList("transactionIndex",
                "parent",
                "previous",
                "transactionRecord.receipt_.exchangeRate_"
        ));

        switch (expected.getTransactionType()) {
            case 8 :// :CONTRACT CREATE INSTANCE
                // Field not used by importer
                ignoreFields.add("transactionRecord.body_.errorMessage_");
                // Ticket 10590
                ignoreFields.add("transactionRecord.receipt_.contractID_");
                break;
            case 15: // CRYPTO UPDATE ACCOUNT
                // This value is parsed from the transaction body, so the receipt value is not needed
                ignoreFields.add("transactionRecord.receipt_.accountID_");
                break;
            case 29: // TOKEN CREATE
                // Importer parses total supply from transaction body initial supply, so this value is not needed
                ignoreFields.add("transactionRecord.receipt_.newTotalSupply_");
                break;
            case 43: // SCHEDULE DELETE
                // This value is parsed from the transaction body, so the receipt value is not needed
                ignoreFields.add("transactionRecord.receipt_.scheduleID_");
                break;
        }

        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*memoizedIsInitialized", ".*memoizedSize", ".*memoizedHashCode", ".*memoizedIsInitialized")
                .ignoringFields(
                        ignoreFields.toArray(new String[0])
                       )
                .isEqualTo(expected);
    }

}
