// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
@Tag("migration")
class ContractLogIndexesMigrationTest extends ImporterIntegrationTest {

    private final ContractLogIndexesMigration migration;

    @Test
    void migrateSuccessful() {
        // Given
        // Persist record files
        for (int index = 0; index < 3; index++) {
            recordFilePersist(index);
        }
        // Persist contract logs
        // First block - contract log indexes are shuffled.
        final var contractLogFirstRecordFile0 = contractLogPersist(2, 25);
        final var contractLogFirstRecordFile1 = contractLogPersist(0, 55);
        final var contractLogFirstRecordFile2 = contractLogPersist(1, 45);

        // Second block - empty
        // Third block - the indexes are all zeroes
        final var contractLogThirdRecordFile0 = contractLogPersist(0, 105);
        final var contractLogThirdRecordFile1 = contractLogPersist(0, 115);
        final var contractLogThirdRecordFile2 = contractLogPersist(0, 135);

        // When
        migration.doMigrate();

        // Then
        assertThat(findIndex(contractLogFirstRecordFile0.getConsensusTimestamp()))
                .isEqualTo(0);
        assertThat(findIndex(contractLogFirstRecordFile1.getConsensusTimestamp()))
                .isEqualTo(2);
        assertThat(findIndex(contractLogFirstRecordFile2.getConsensusTimestamp()))
                .isEqualTo(1);

        assertThat(findIndex(contractLogThirdRecordFile0.getConsensusTimestamp()))
                .isEqualTo(0);
        assertThat(findIndex(contractLogThirdRecordFile1.getConsensusTimestamp()))
                .isEqualTo(1);
        assertThat(findIndex(contractLogThirdRecordFile2.getConsensusTimestamp()))
                .isEqualTo(2);
    }

    private Integer findIndex(final long consensusTimestamp) {
        var query = "select index from contract_log where consensus_timestamp = ?";
        return jdbcOperations.queryForObject(query, Integer.class, consensusTimestamp);
    }

    private ContractLog contractLogPersist(final int contractLogIndex, final long consensusTimestamp) {
        return domainBuilder
                .contractLog()
                .customize(cl -> cl.index(contractLogIndex).consensusTimestamp(consensusTimestamp))
                .persist();
    }

    private void recordFilePersist(final long index) {
        domainBuilder
                .recordFile()
                .customize(r -> r.index(index).consensusStart(index * 100).consensusEnd(index * 100 + 99))
                .persist();
    }
}
