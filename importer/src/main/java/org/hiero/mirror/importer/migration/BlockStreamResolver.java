// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.hiero.mirror.common.domain.transaction.RecordFile.GENESIS_BLOCK_NUMBER;

import jakarta.inject.Named;
import java.util.Optional;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
@RequiredArgsConstructor
final class BlockStreamResolver {

    // Queried via the DataSource directly since this runs inside flyway migrations, where resolving a Spring Data
    // JDBC repository would deadlock on the database initialization ordering (repository infrastructure ->
    // NamedParameterJdbcTemplate -> flywayInitializer)
    private static final String FIRST_RECORD_FILE_SQL = """
            select index, version, wrapped_record_block_hash is not null as wrapped
            from record_file
            order by consensus_end
            limit 1
            """;

    private final BlockProperties blockProperties;
    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ImporterProperties importerProperties;

    /**
     * Whether the initial state has been, or will be, loaded from the genesis wrapped record block, i.e., the first
     * ingested block is the genesis block ({@code index == 0}) and a wrapped record block, or nothing has been ingested
     * yet and the importer is configured to ingest the block stream starting from genesis.
     */
    boolean isInitialStateFromGenesisWrb() {
        final var firstRecordFile = findFirstRecordFile();
        if (firstRecordFile.isPresent()) {
            final var recordFile = firstRecordFile.get();
            final var index = recordFile.index();
            return recordFile.wrapped() && index != null && index == GENESIS_BLOCK_NUMBER;
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
        final var firstRecordFile = findFirstRecordFile();
        if (firstRecordFile.isPresent()) {
            final var recordFile = firstRecordFile.get();
            return recordFile.wrapped() || recordFile.version() == BlockStreamReader.VERSION;
        }

        return blockProperties.isEnabled();
    }

    MigrationVersion getMinimumMigrationVersion(final boolean v2) {
        // The minimum version is the one record_file.wrapped_record_block_hash is added
        return v2 ? MigrationVersion.fromVersion("2.25.0") : MigrationVersion.fromVersion("1.120.0");
    }

    private Optional<FirstRecordFile> findFirstRecordFile() {
        final var jdbcTemplate = new JdbcTemplate(dataSourceProvider.getObject());
        final var rows = jdbcTemplate.query(
                FIRST_RECORD_FILE_SQL,
                (rs, rowNum) -> new FirstRecordFile(
                        rs.getObject("index", Long.class), rs.getInt("version"), rs.getBoolean("wrapped")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private record FirstRecordFile(Long index, int version, boolean wrapped) {}
}
