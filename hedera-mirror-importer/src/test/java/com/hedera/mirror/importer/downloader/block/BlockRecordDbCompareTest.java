// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block;

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.parser.batch.BatchPersister;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import io.micrometer.common.util.StringUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@CustomLog
@RequiredArgsConstructor
class BlockRecordDbCompareTest extends ImporterIntegrationTest {

    private final BlockFileTransformer blockFileTransformer;
    private final ConsensusNodeService consensusNodeService;
    private final RecordFileParser recordFileParser;

    private final BlockDownloadUtility blockDownloadUtility;

    @AfterEach
    void shutdown() {
        Thread.currentThread().interrupt();
    }

    @Test
    void compare() {
        long initialBlockNumber = 39364290; // testnet acceptance tests
        initialBlockNumber = 41464647;

        var consensusNode = consensusNodeService.getNodes().stream().filter(n -> n.getNodeId() == 0).findFirst().get();

        long blockNumber = initialBlockNumber;
        var streamFilename = StreamFilename.from(blockNumber);
        List<Integer> skippedTransactionTypes = List.of();

        Map<String, String> blockCsv;
        Map<String, String> recordCsv;

        while (true) {
            Long firstConsensus = 0L;
            Long lastConsensus = 0L;

            var allTransformedRecordItems = new ArrayList<RecordItem>();
            int blocksToDownload = 100;
            while (blocksToDownload > 0) {
                BlockFile blockFile;
                try {
                    blockFile = blockDownloadUtility.downloadBlock(consensusNode, streamFilename);
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
                        .filter(r -> !(r.getTransactionRecord().getMemo().contains("Monitor pinger") ||
                                r.getTransactionRecord().getMemo().contains("hedera-mirror-monitor")))
                        .toList();
                if(transformedRecordItems.isEmpty()) {
                    blockNumber++;
                    streamFilename = StreamFilename.from(blockNumber);
                    continue;
                }

                firstConsensus = firstConsensus == 0L ? transformedRecordFile.getConsensusStart() : firstConsensus;
                lastConsensus = transformedRecordFile.getConsensusEnd();

                allTransformedRecordItems.addAll(transformedRecordItems);
                blockNumber = blockFile.getBlockHeader().getNumber() + 1;
                streamFilename = StreamFilename.from(blockNumber);
                blocksToDownload--;
            }

            var allRecordItems = new ArrayList<RecordItem>();
            if(!allTransformedRecordItems.isEmpty()) {
                // Set timestamp back 4 seconds to make sure the range of transactions match up to that of the block
                var firstBlockInstant = Instant.ofEpochSecond(0, firstConsensus - 4000000000L);
                String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, firstBlockInstant);

                while(true) {
                    var recordFile = blockDownloadUtility.downloadRecordFile(consensusNode, filename);
                    if(recordFile.getConsensusEnd() < firstConsensus) {
                        filename = recordFile.getName();
                        continue;
                    }

                    Long finalFirstConsensus = firstConsensus;
                    Long finalLastConsensus = lastConsensus;
                    var recordItems = recordFile.getItems().stream()
                            .filter(r -> !r.getTransaction().getSignedTransactionBytes().isEmpty()) // Do not process block items with no signed transaction bytes - Ticket 10552
                            .filter(r -> !(r.getTransactionRecord().getMemo().contains("Monitor pinger") ||
                                    r.getTransactionRecord().getMemo().contains("hedera-mirror-monitor")))
                            .filter(r -> r.getConsensusTimestamp() >= finalFirstConsensus
                                    && r.getConsensusTimestamp() <= finalLastConsensus)
                            .toList();
                    allRecordItems.addAll(recordItems);
                    if(recordFile.getConsensusEnd() > lastConsensus) {
                        break;
                    }

                    filename = recordFile.getName();
                }

                for (RecordItem item : allTransformedRecordItems) {
                    var memo = item.getTransactionRecord().getMemo();
                    if (!StringUtils.isEmpty(memo)) {
                        log.info("Block Transaction memo: " + memo);
                    }
                }

                var sanitizedTransformedRecordFile = RecordFile.builder()
                        .name(filename)
                        .nodeId(0L)
                        .consensusEnd(lastConsensus)
                        .consensusStart(firstConsensus)
                        .fileHash(org.apache.commons.lang3.StringUtils.EMPTY)
                        .index(0L)
                        .items(allTransformedRecordItems)
                        .loadEnd(lastConsensus)
                        .loadStart(firstConsensus)
                        .build();
                recordFileParser.parse(sanitizedTransformedRecordFile);
                blockCsv = new HashMap<>(BatchPersister.insertCsv);
                BatchPersister.insertCsv.clear();

                var sanitizedRecordFile = RecordFile.builder()
                        .name(filename)
                        .nodeId(0L)
                        .consensusEnd(lastConsensus)
                        .consensusStart(firstConsensus)
                        .fileHash(org.apache.commons.lang3.StringUtils.EMPTY)
                        .index(0L)
                        .items(allRecordItems)
                        .loadEnd(lastConsensus)
                        .loadStart(firstConsensus)
                        .build();
                recordFileParser.parse(sanitizedRecordFile);
                recordCsv = new HashMap<>(BatchPersister.insertCsv);
                BatchPersister.insertCsv.clear();

                assertThat(blockCsv.keySet()).isEqualTo(recordCsv.keySet());
                for(var table : recordCsv.keySet()) {
                    assertThat(blockCsv.get(table)).isEqualTo(recordCsv.get(table));
                }

                blockCsv.clear();
                recordCsv.clear();
            }
        }
    }

}
