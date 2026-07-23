// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.Objects;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
final class BlockNumberMigration extends RepeatableMigration {

    static final long MAINNET_BLOCK_CONSENSUS_END = 1656461547557609267L;
    static final long MAINNET_BLOCK_NUMBER = 34305852L;

    private final BlockStreamResolver blockStreamResolver;
    private final ImporterProperties importerProperties;
    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final ObjectProvider<RecordFileRepository> recordFileRepositoryProvider;
    private final boolean v2;

    BlockNumberMigration(
            BlockStreamResolver blockStreamResolver,
            Environment environment,
            ImporterProperties importerProperties,
            ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            ObjectProvider<RecordFileRepository> recordFileRepositoryProvider) {
        super(importerProperties.getMigration());
        this.blockStreamResolver = blockStreamResolver;
        this.importerProperties = importerProperties;
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.recordFileRepositoryProvider = recordFileRepositoryProvider;
        v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Updates the incorrect index from the record file table when necessary";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return blockStreamResolver.getMinimumMigrationVersion(v2);
    }

    @Override
    protected void doMigrate() {
        final var hederaNetwork = importerProperties.getNetwork();
        if (!Objects.equals(hederaNetwork, ImporterProperties.HederaNetwork.MAINNET)) {
            log.info("No block migration necessary for {} network", hederaNetwork);
            return;
        }

        try {
            final var params = new MapSqlParameterSource().addValue("consensusEnd", MAINNET_BLOCK_CONSENSUS_END);
            final var index = jdbcOperationsProvider
                    .getObject()
                    .queryForObject(
                            "select index from record_file where consensus_end = :consensusEnd limit 1",
                            params,
                            Long.class);
            if (index != null && index != MAINNET_BLOCK_NUMBER) {
                final long offset = MAINNET_BLOCK_NUMBER - index;
                final var stopwatch = Stopwatch.createStarted();
                final int count = recordFileRepositoryProvider.getObject().updateIndex(offset);
                log.info("Updated {} blocks with offset {} in {}", count, offset, stopwatch);
            }
        } catch (final IncorrectResultSizeDataAccessException _) {
            // empty record_file table, ignore
        }
    }

    @Override
    protected boolean skipMigration(Configuration configuration) {
        if (super.skipMigration(configuration)) {
            return true;
        }

        if (blockStreamResolver.isStartedFromBlockStream()) {
            log.info("Skip migration since the importer started from the block stream");
            return true;
        }

        return false;
    }
}
