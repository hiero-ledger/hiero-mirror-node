// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.migration.ContractLogSyntheticBackfillMigration.BATCH_INTERVAL;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
class ContractLogSyntheticBackfillMigrationTest
        extends AbstractAsyncJavaMigrationTest<ContractLogSyntheticBackfillMigration> {

    @Getter
    private final ContractLogSyntheticBackfillMigration migration;

    private final ContractLogRepository contractLogRepository;

    @BeforeEach
    void dropProgressTable() {
        ownerJdbcTemplate.execute("drop table if exists contract_log_synthetic_progress_temp");
    }

    @Test
    void emptyDatabase() {
        runMigration();
        waitForCompletion();

        assertThat(contractLogRepository.findAll()).isEmpty();
    }

    @Test
    void backfillsNullRowsWithoutContractResult() {
        // given
        var contractLog1 = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L))
                .persist();
        var contractLog2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(2000L))
                .persist();
        nullifySynthetic(contractLog1.getConsensusTimestamp());
        nullifySynthetic(contractLog2.getConsensusTimestamp());

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(findSynthetic(contractLog1.getConsensusTimestamp())).isTrue();
        assertThat(findSynthetic(contractLog2.getConsensusTimestamp())).isTrue();
    }

    @Test
    void preservesRowsWithMatchingContractResult() {
        // given
        var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L))
                .persist();
        domainBuilder
                .contractResult()
                .customize(cr -> cr.contractId(contractLog.getContractId().getId())
                        .consensusTimestamp(contractLog.getConsensusTimestamp()))
                .persist();
        nullifySynthetic(contractLog.getConsensusTimestamp());

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(findSynthetic(contractLog.getConsensusTimestamp())).isNull();
    }

    @Test
    void backfillsNullRowWhenContractResultHasDifferentContractId() {
        // given
        var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L))
                .persist();
        domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(contractLog.getConsensusTimestamp()))
                .persist();
        nullifySynthetic(contractLog.getConsensusTimestamp());

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(findSynthetic(contractLog.getConsensusTimestamp())).isTrue();
    }

    @Test
    void backfillsNullRowWhenContractResultHasDifferentTimestamp() {
        // given
        var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L))
                .persist();
        domainBuilder
                .contractResult()
                .customize(
                        cr -> cr.contractId(contractLog.getContractId().getId()).consensusTimestamp(2000L))
                .persist();
        nullifySynthetic(contractLog.getConsensusTimestamp());

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(findSynthetic(contractLog.getConsensusTimestamp())).isTrue();
    }

    @Test
    void preservesAlreadyFlaggedRows() {
        // given
        var syntheticTrue = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L).synthetic(true))
                .persist();
        var syntheticFalse = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(2000L).synthetic(false))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(findSynthetic(syntheticTrue.getConsensusTimestamp())).isTrue();
        assertThat(findSynthetic(syntheticFalse.getConsensusTimestamp())).isFalse();
    }

    @Test
    void processesMultipleBatchIntervals() {
        // given
        var early = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L))
                .persist();
        var late = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L + BATCH_INTERVAL + 1))
                .persist();
        nullifySynthetic(early.getConsensusTimestamp());
        nullifySynthetic(late.getConsensusTimestamp());

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(findSynthetic(early.getConsensusTimestamp())).isTrue();
        assertThat(findSynthetic(late.getConsensusTimestamp())).isTrue();
    }

    private Boolean findSynthetic(long consensusTimestamp) {
        return jdbcOperations.queryForObject(
                "select synthetic from contract_log where consensus_timestamp = ?", Boolean.class, consensusTimestamp);
    }

    private void nullifySynthetic(long consensusTimestamp) {
        jdbcOperations.update(
                "update contract_log set synthetic = null where consensus_timestamp = ?", consensusTimestamp);
    }
}
