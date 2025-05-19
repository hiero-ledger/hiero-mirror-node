// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.PREVIEWNET;
import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.TESTNET;
import static org.hiero.mirror.importer.migration.BlockNumberMigration.BLOCK_NUMBER_MAPPING;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.assertj.core.groups.Tuple;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
@Tag("migration")
class BlockNumberMigrationTest extends ImporterIntegrationTest {

    private static final long CORRECT_CONSENSUS_END =
            BLOCK_NUMBER_MAPPING.get(TESTNET).getKey();
    private static final long CORRECT_BLOCK_NUMBER =
            BLOCK_NUMBER_MAPPING.get(TESTNET).getValue();

    private final BlockNumberMigration blockNumberMigration;
    private final ImporterProperties importerProperties;
    private final RecordFileRepository recordFileRepository;

    @BeforeEach
    void setup() {
        importerProperties.setNetwork(TESTNET);
    }

    @Test
    void checksum() {
        assertThat(blockNumberMigration.getChecksum()).isEqualTo(4);
    }

    @Test
    void unsupportedNetwork() {
        var previousNetwork = importerProperties.getNetwork();
        importerProperties.setNetwork(PREVIEWNET);
        List<Tuple> expectedBlockNumbersAndConsensusEnd =
                insertDefaultRecordFiles(Set.of(CORRECT_CONSENSUS_END)).stream()
                        .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                        .toList();

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
        importerProperties.setNetwork(previousNetwork);
    }

    @Test
    void theCorrectOffsetMustBeAddedToTheBlockNumbers() {
        List<RecordFile> defaultRecordFiles = insertDefaultRecordFiles();
        long offset = CORRECT_BLOCK_NUMBER - 8L;
        List<Tuple> expectedBlockNumbersAndConsensusEnd = defaultRecordFiles.stream()
                .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex() + offset))
                .toList();

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @Test
    void ifCorrectConsensusEndNotFoundDoNothing() {
        List<Tuple> expectedBlockNumbersAndConsensusEnd =
                insertDefaultRecordFiles(Set.of(CORRECT_CONSENSUS_END)).stream()
                        .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                        .toList();

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @Test
    void ifBlockNumberIsAlreadyCorrectDoNothing() {
        List<Tuple> expectedBlockNumbersAndConsensusEnd =
                insertDefaultRecordFiles(Set.of(CORRECT_CONSENSUS_END)).stream()
                        .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                        .collect(Collectors.toList());

        final RecordFile targetRecordFile = domainBuilder
                .recordFile()
                .customize(
                        builder -> builder.consensusEnd(CORRECT_CONSENSUS_END).index(CORRECT_BLOCK_NUMBER))
                .persist();
        expectedBlockNumbersAndConsensusEnd.add(
                Tuple.tuple(targetRecordFile.getConsensusEnd(), targetRecordFile.getIndex()));

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    private void assertConsensusEndAndBlockNumber(List<Tuple> expectedBlockNumbersAndConsensusEnd) {
        assertThat(recordFileRepository.findAll())
                .hasSize(expectedBlockNumbersAndConsensusEnd.size())
                .extracting(RecordFile::getConsensusEnd, RecordFile::getIndex)
                .containsExactlyInAnyOrder(expectedBlockNumbersAndConsensusEnd.toArray(Tuple[]::new));
    }

    private List<RecordFile> insertDefaultRecordFiles() {
        return insertDefaultRecordFiles(Set.of());
    }

    private List<RecordFile> insertDefaultRecordFiles(Set<Long> skipRecordFileWithConsensusEnd) {
        long[] consensusEnd = {1570800761443132000L, CORRECT_CONSENSUS_END, CORRECT_CONSENSUS_END + 1L};
        long[] blockNumber = {0L, 8L, 9L};
        var recordFiles = new ArrayList<RecordFile>(consensusEnd.length);

        for (int i = 0; i < consensusEnd.length; i++) {
            if (skipRecordFileWithConsensusEnd.contains(consensusEnd[i])) {
                continue;
            }
            final long currConsensusEnd = consensusEnd[i];
            final long currBlockNumber = blockNumber[i];
            RecordFile recordFile = domainBuilder
                    .recordFile()
                    .customize(builder -> builder.consensusEnd(currConsensusEnd).index(currBlockNumber))
                    .persist();
            recordFiles.add(recordFile);
        }

        return recordFiles;
    }
}
