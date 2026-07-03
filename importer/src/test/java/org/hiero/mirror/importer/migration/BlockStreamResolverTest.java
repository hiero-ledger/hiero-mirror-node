// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.reader.record.ProtoRecordFileReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class BlockStreamResolverTest extends ImporterIntegrationTest {

    private final BlockProperties blockProperties;
    private final BlockStreamResolver blockStreamResolver;
    private final ImporterProperties importerProperties;

    @AfterEach
    void teardown() {
        blockProperties.setEnabled(false);
        importerProperties.setStartBlockNumber(null);
    }

    @Test
    void genesisWrbIngested() {
        persistRecordFile(0L, ProtoRecordFileReader.VERSION, true);
        assertThat(blockStreamResolver.isInitialStateFromGenesisWrb()).isTrue();
        assertThat(blockStreamResolver.isStartedFromBlockStream()).isTrue();
    }

    @Test
    void nonGenesisWrbIngested() {
        persistRecordFile(10L, ProtoRecordFileReader.VERSION, true);
        assertThat(blockStreamResolver.isInitialStateFromGenesisWrb()).isFalse();
        assertThat(blockStreamResolver.isStartedFromBlockStream()).isTrue();
    }

    @Test
    void blockStreamBlockIngested() {
        // A block stream block, not a wrapped record block: no wrappedRecordBlockHash, version >= block stream version
        persistRecordFile(10L, BlockStreamReader.VERSION, false);
        assertThat(blockStreamResolver.isInitialStateFromGenesisWrb()).isFalse();
        assertThat(blockStreamResolver.isStartedFromBlockStream()).isTrue();
    }

    @Test
    void recordStreamIngested() {
        // A legacy record stream file: no wrappedRecordBlockHash and version below the block stream version
        persistRecordFile(0L, ProtoRecordFileReader.VERSION, false);
        assertThat(blockStreamResolver.isInitialStateFromGenesisWrb()).isFalse();
        assertThat(blockStreamResolver.isStartedFromBlockStream()).isFalse();
    }

    @Test
    void noBlocksBlockStreamDisabled() {
        blockProperties.setEnabled(false);
        importerProperties.setStartBlockNumber(null);
        assertThat(blockStreamResolver.isInitialStateFromGenesisWrb()).isFalse();
        assertThat(blockStreamResolver.isStartedFromBlockStream()).isFalse();
    }

    @Test
    void noBlocksConfiguredFromGenesisNullStart() {
        blockProperties.setEnabled(true);
        importerProperties.setStartBlockNumber(null);
        assertThat(blockStreamResolver.isInitialStateFromGenesisWrb()).isTrue();
        assertThat(blockStreamResolver.isStartedFromBlockStream()).isTrue();
    }

    @Test
    void noBlocksConfiguredFromGenesisZeroStart() {
        blockProperties.setEnabled(true);
        importerProperties.setStartBlockNumber(0L);
        assertThat(blockStreamResolver.isInitialStateFromGenesisWrb()).isTrue();
        assertThat(blockStreamResolver.isStartedFromBlockStream()).isTrue();
    }

    @Test
    void noBlocksConfiguredFromNonGenesis() {
        blockProperties.setEnabled(true);
        importerProperties.setStartBlockNumber(5L);
        assertThat(blockStreamResolver.isInitialStateFromGenesisWrb()).isFalse();
        assertThat(blockStreamResolver.isStartedFromBlockStream()).isTrue();
    }

    @Test
    void getMinimumMigrationVersion() {
        assertThat(blockStreamResolver.getMinimumMigrationVersion(true))
                .isEqualTo(MigrationVersion.fromVersion("2.25.0"));
        assertThat(blockStreamResolver.getMinimumMigrationVersion(false))
                .isEqualTo(MigrationVersion.fromVersion("1.120.0"));
    }

    private void persistRecordFile(final long index, final int version, final boolean wrapped) {
        final byte[] wrappedRecordBlockHash = wrapped ? domainBuilder.bytes(48) : null;
        domainBuilder
                .recordFile()
                .customize(r -> r.index(index).version(version).wrappedRecordBlockHash(wrappedRecordBlockHash))
                .persist();
    }
}
