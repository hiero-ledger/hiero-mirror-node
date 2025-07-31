// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.MAINNET;
import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.TESTNET;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Named
public class BlockNumberMigration extends RepeatableMigration {

    static final Map<String, Pair<Long, Long>> BLOCK_NUMBER_MAPPING = Map.of(
            TESTNET, Pair.of(1656461617493248000L, 22384256L),
            MAINNET, Pair.of(1656461547557609267L, 34305852L));

    private final ImporterProperties importerProperties;
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final ObjectProvider<RecordFileRepository> recordFileRepositoryProvider;

    public BlockNumberMigration(
            ImporterProperties importerProperties,
            ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<RecordFileRepository> recordFileRepositoryProvider) {
        super(importerProperties.getMigration());
        this.importerProperties = importerProperties;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.recordFileRepositoryProvider = recordFileRepositoryProvider;
    }

    @Override
    public String getDescription() {
        return "Updates the incorrect index from the record file table when necessary";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.67.0");
    }

    @Override
    protected void doMigrate() {
        var hederaNetwork = importerProperties.getNetwork();
        var consensusEndAndBlockNumber = BLOCK_NUMBER_MAPPING.get(hederaNetwork);

        if (consensusEndAndBlockNumber == null) {
            log.info("No block migration necessary for {} network", hederaNetwork);
            return;
        }

        long correctConsensusEnd = consensusEndAndBlockNumber.getKey();
        long correctBlockNumber = consensusEndAndBlockNumber.getValue();

        findBlockNumberByConsensusEnd(correctConsensusEnd)
                .filter(blockNumber -> blockNumber != correctBlockNumber)
                .ifPresent(blockNumber -> updateIndex(correctBlockNumber, blockNumber));
    }

    private void updateIndex(long correctBlockNumber, long incorrectBlockNumber) {
        long offset = correctBlockNumber - incorrectBlockNumber;
        Stopwatch stopwatch = Stopwatch.createStarted();
        int count = recordFileRepositoryProvider.getObject().updateIndex(offset);
        log.info("Updated {} blocks with offset {} in {}", count, offset, stopwatch);
    }

    private Optional<Long> findBlockNumberByConsensusEnd(long consensusEnd) {
        var params = new MapSqlParameterSource().addValue("consensusEnd", consensusEnd);
        try {
            return Optional.ofNullable(jdbcTemplateProvider
                    .getObject()
                    .queryForObject(
                            "select index from record_file where consensus_end = :consensusEnd limit 1",
                            params,
                            Long.class));
        } catch (IncorrectResultSizeDataAccessException ex) {
            return Optional.empty();
        }
    }
}
