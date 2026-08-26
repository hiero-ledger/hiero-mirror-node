// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.hiero.mirror.common.domain.transaction.RecordFile.GENESIS_BLOCK_NUMBER;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.springframework.beans.factory.ObjectProvider;

@Named
@RequiredArgsConstructor
final class BlockStreamResolver {

    private final BlockProperties blockProperties;
    private final ImporterProperties importerProperties;
    private final ObjectProvider<RecordFileRepository> recordFileRepositoryProvider;

    /**
     * Whether the initial state has been, or will be, loaded from the genesis wrapped record block, i.e., the first
     * ingested block is the genesis block ({@code index == 0}) and a wrapped record block, or nothing has been ingested
     * yet and the importer is configured to ingest the block stream starting from genesis.
     */
    boolean isInitialStateFromGenesisWrb() {
        final var firstRecordFile = recordFileRepositoryProvider.getObject().findFirst();
        if (firstRecordFile.isPresent()) {
            final var recordFile = firstRecordFile.get();
            final var index = recordFile.getIndex();
            return recordFile.getWrappedRecordBlockHash() != null && index != null && index == GENESIS_BLOCK_NUMBER;
        }

        final var startBlockNumber = importerProperties.getStartBlockNumber();
        return blockProperties.isEnabled() && (startBlockNumber == null || startBlockNumber == 0L);
    }

    /**
     * Whether the importer started, or will start, from the block stream, i.e., the first ingested block is a wrapped
     * record block or a block stream block regardless of its index, or nothing has been ingested yet and the importer
     * is configured to ingest the block stream from any block.
     */
    boolean isStartedFromBlockStream() {
        final var firstRecordFile = recordFileRepositoryProvider.getObject().findFirst();
        if (firstRecordFile.isPresent()) {
            final var recordFile = firstRecordFile.get();
            return recordFile.getWrappedRecordBlockHash() != null
                    || recordFile.getVersion() == BlockStreamReader.VERSION;
        }

        return blockProperties.isEnabled();
    }

    MigrationVersion getMinimumMigrationVersion(final boolean v2) {
        // The minimum version is the one record_file.wrapped_record_block_hash is added
        return v2 ? MigrationVersion.fromVersion("2.25.0") : MigrationVersion.fromVersion("1.120.0");
    }
}
