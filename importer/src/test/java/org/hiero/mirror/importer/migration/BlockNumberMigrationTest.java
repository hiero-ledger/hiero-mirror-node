// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.MAINNET;
import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.PREVIEWNET;
import static org.hiero.mirror.importer.migration.BlockNumberMigration.MAINNET_BLOCK_CONSENSUS_END;
import static org.hiero.mirror.importer.migration.BlockNumberMigration.MAINNET_BLOCK_NUMBER;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.assertj.core.groups.Tuple;
import org.flywaydb.core.api.configuration.Configuration;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
@Tag("migration")
final class BlockNumberMigrationTest extends ImporterIntegrationTest {

    private final BlockNumberMigration blockNumberMigration;
    private final BlockProperties blockProperties;
    private final ImporterProperties importerProperties;
    private final RecordFileRepository recordFileRepository;

    @BeforeEach
    void setup() {
        importerProperties.setNetwork(MAINNET);
    }

    @AfterEach
    void teardown() {
        blockProperties.setEnabled(false);
    }

    @Test
    void checksum() {
        assertThat(blockNumberMigration.getChecksum()).isEqualTo(4);
    }

    @Test
    void unsupportedNetwork() {
        importerProperties.setNetwork(PREVIEWNET);
        List<Tuple> expectedBlockNumbersAndConsensusEnd =
                insertDefaultRecordFiles(Set.of(MAINNET_BLOCK_CONSENSUS_END)).stream()
                        .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                        .toList();

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @Test
    void theCorrectOffsetMustBeAddedToTheBlockNumbers() {
        List<RecordFile> defaultRecordFiles = insertDefaultRecordFiles();
        long offset = MAINNET_BLOCK_NUMBER - 8L;
        List<Tuple> expectedBlockNumbersAndConsensusEnd = defaultRecordFiles.stream()
                .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex() + offset))
                .toList();

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @Test
    void ifCorrectConsensusEndNotFoundDoNothing() {
        List<Tuple> expectedBlockNumbersAndConsensusEnd =
                insertDefaultRecordFiles(Set.of(MAINNET_BLOCK_CONSENSUS_END)).stream()
                        .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                        .toList();

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @Test
    void ifBlockNumberIsAlreadyCorrectDoNothing() {
        List<Tuple> expectedBlockNumbersAndConsensusEnd =
                insertDefaultRecordFiles(Set.of(MAINNET_BLOCK_CONSENSUS_END)).stream()
                        .map(recordFile -> Tuple.tuple(recordFile.getConsensusEnd(), recordFile.getIndex()))
                        .collect(Collectors.toList());

        final RecordFile targetRecordFile = domainBuilder
                .recordFile()
                .customize(builder ->
                        builder.consensusEnd(MAINNET_BLOCK_CONSENSUS_END).index(MAINNET_BLOCK_NUMBER))
                .persist();
        expectedBlockNumbersAndConsensusEnd.add(
                Tuple.tuple(targetRecordFile.getConsensusEnd(), targetRecordFile.getIndex()));

        blockNumberMigration.doMigrate();

        assertConsensusEndAndBlockNumber(expectedBlockNumbersAndConsensusEnd);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void skipWhenIngestedFromBlockstream(final boolean wrapped) {
        // given
        domainBuilder
                .recordFile()
                .customize(r -> {
                    if (wrapped) {
                        r.wrappedRecordBlockHash(domainBuilder.bytes(48))
                                .previousWrappedRecordBlockHash(domainBuilder.bytes(48));
                    } else {
                        r.version(BlockStreamReader.VERSION);
                    }
                })
                .persist();

        // when, then
        assertThat(blockNumberMigration.skipMigration(mock(Configuration.class)))
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            true, true
            false, false
            """)
    void skipWithBlockStreamConfig(final boolean enabled, final boolean shouldSkip) {
        blockProperties.setEnabled(enabled);
        assertThat(blockNumberMigration.skipMigration(mock(Configuration.class)))
                .isEqualTo(shouldSkip);
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
        long[] consensusEnd = {1570800761443132000L, MAINNET_BLOCK_CONSENSUS_END, MAINNET_BLOCK_CONSENSUS_END + 1L};
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
