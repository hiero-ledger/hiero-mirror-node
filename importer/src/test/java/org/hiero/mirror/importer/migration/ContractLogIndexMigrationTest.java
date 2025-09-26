// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.migration.ContractLogIndexMigration.INTERVAL;

import java.time.Duration;
import java.util.ArrayList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
@Tag("migration")
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
class ContractLogIndexMigrationTest extends AbstractAsyncJavaMigrationTest<ContractLogIndexMigration> {

    @Getter
    private final ContractLogIndexMigration migration;

    private final RecordFileRepository recordFileRepository;
    private final ContractLogRepository contractLogRepository;
    private final EntityProperties entityProperties;

    @Test
    void migrationOnEmptyDB() {
        // given, when
        runMigration();
        waitForCompletion();

        // then
        assertThat(recordFileRepository.findAll()).isEmpty();
        assertThat(contractLogRepository.findAll()).isEmpty();
        assertThat(tableExists("processed_record_file_temp")).isFalse();
    }

    @Test
    void migrateSuccessful() {
        // given
        // Persist record files
        final var recordFiles = new ArrayList<RecordFile>();
        for (int index = 0; index < 4; index++) {
            recordFiles.add(recordFilePersist(index));
        }
        // Persist contract logs
        // First block - contract log indexes are shuffled and two of them are in the same transaction.
        final var contractLogFirstRecordFile0 =
                contractLogPersist(2, recordFiles.get(0).getConsensusStart());
        final var contractLogFirstRecordFile1 =
                contractLogPersist(0, recordFiles.get(0).getConsensusEnd());
        final var contractLogFirstRecordFile2 =
                contractLogPersist(1, recordFiles.get(0).getConsensusEnd());

        // Second block - empty
        // Third block - the indexes are all zeroes
        final var contractLogThirdRecordFile0 =
                contractLogPersist(0, recordFiles.get(2).getConsensusStart());
        final var contractLogThirdRecordFile1 =
                contractLogPersist(0, recordFiles.get(2).getConsensusEnd() - 1);
        final var contractLogThirdRecordFile2 =
                contractLogPersist(0, recordFiles.get(2).getConsensusEnd());

        // Fourth block - contract log indexes are shuffled and two of them are in the same transaction.
        final var contractLogFourthRecordFile0 =
                contractLogPersist(2, recordFiles.get(3).getConsensusStart());
        final var contractLogFourthRecordFile1 =
                contractLogPersist(1, recordFiles.get(3).getConsensusEnd());
        final var contractLogFourthRecordFile2 =
                contractLogPersist(0, recordFiles.get(3).getConsensusEnd());

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(findIndex(contractLogFirstRecordFile0.getConsensusTimestamp()))
                .isEqualTo(0);
        assertThat(findIndexForData(
                        contractLogFirstRecordFile1.getConsensusTimestamp(), contractLogFirstRecordFile1.getData()))
                .isEqualTo(1);
        assertThat(findIndexForData(
                        contractLogFirstRecordFile2.getConsensusTimestamp(), contractLogFirstRecordFile2.getData()))
                .isEqualTo(2);

        assertThat(findIndex(contractLogThirdRecordFile0.getConsensusTimestamp()))
                .isEqualTo(0);
        assertThat(findIndex(contractLogThirdRecordFile1.getConsensusTimestamp()))
                .isEqualTo(1);
        assertThat(findIndex(contractLogThirdRecordFile2.getConsensusTimestamp()))
                .isEqualTo(2);

        assertThat(findIndex(contractLogFourthRecordFile0.getConsensusTimestamp()))
                .isEqualTo(0);
        assertThat(findIndexForData(
                        contractLogFourthRecordFile2.getConsensusTimestamp(), contractLogFourthRecordFile2.getData()))
                .isEqualTo(1);
        assertThat(findIndexForData(
                        contractLogFourthRecordFile1.getConsensusTimestamp(), contractLogFourthRecordFile1.getData()))
                .isEqualTo(2);
        assertThat(tableExists("processed_record_file_temp")).isFalse();
    }

    private Integer findIndex(final long consensusTimestamp) {
        var query = "select index from contract_log where consensus_timestamp = ?";
        return jdbcOperations.queryForObject(query, Integer.class, consensusTimestamp);
    }

    // In our example 2 contract logs are part of the same transaction, so they have the same
    // transaction index, the same consensus timestamp and the contract log index is being changed
    // so in order to get a particular contract log and compare the indexes, we will use
    // the data as it is generated per contract log entry in the domain builder.
    private Integer findIndexForData(final long consensusTimestamp, final byte[] data) {
        var query = "select index from contract_log where consensus_timestamp = ? and data = ?";
        return jdbcOperations.queryForObject(query, Integer.class, consensusTimestamp, data);
    }

    private ContractLog contractLogPersist(final int contractLogIndex, final long consensusTimestamp) {
        return domainBuilder
                .contractLog()
                .customize(cl -> cl.index(contractLogIndex).consensusTimestamp(consensusTimestamp))
                .persist();
    }

    private RecordFile recordFilePersist(final long index) {
        final var timestamp = domainBuilder.timestamp() + index * INTERVAL;
        return domainBuilder
                .recordFile()
                .customize(r -> r.index(index)
                        .consensusStart(timestamp - Duration.ofSeconds(2).toNanos())
                        .consensusEnd(timestamp))
                .persist();
    }
}
