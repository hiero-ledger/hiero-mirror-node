// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class MergeDuplicateBlocksMigrationTest extends ImporterIntegrationTest {

    private static final long NUMBER = 44029066L;
    private static final long TIMESTAMP1 = 1675962000231859003L;
    private static final long TIMESTAMP2 = 1675962001984524003L;

    private final MergeDuplicateBlocksMigration migration;
    private final ImporterProperties importerProperties;
    private final RecordFileRepository recordFileRepository;
    private final TransactionRepository transactionRepository;

    private RecordFile block1;
    private RecordFile block2;

    @BeforeEach
    void setupData() {
        block1 = block(TIMESTAMP1);
        block2 = block(TIMESTAMP2);

        transaction(TIMESTAMP1 - 1, 0);
        transaction(TIMESTAMP1, 1);
        transaction(TIMESTAMP2 - 1, 0);
        transaction(TIMESTAMP2, 1);
    }

    @Test
    void notMainnet() throws Exception {
        // Given
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.TESTNET);

        // When
        migration.doMigrate();

        // Then
        assertThat(recordFileRepository.findById(block1.getConsensusEnd()))
                .get()
                .isEqualTo(block1);
        assertThat(recordFileRepository.findById(block2.getConsensusEnd()))
                .get()
                .isEqualTo(block2);
    }

    @Test
    void mainnet() throws Exception {
        // Given
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.MAINNET);

        // When
        migration.doMigrate();

        // Then
        assertThat(recordFileRepository.existsById(block1.getConsensusEnd())).isFalse();
        assertThat(recordFileRepository.findById(block2.getConsensusEnd()))
                .get()
                .returns(block2.getBytes(), RecordFile::getBytes)
                .returns(block2.getConsensusEnd(), RecordFile::getConsensusEnd)
                .returns(block1.getConsensusStart(), RecordFile::getConsensusStart)
                .returns(block1.getCount() + block2.getCount(), RecordFile::getCount)
                .returns(block2.getDigestAlgorithm(), RecordFile::getDigestAlgorithm)
                .returns(block2.getFileHash(), RecordFile::getFileHash)
                .returns(block1.getGasUsed() + block2.getGasUsed(), RecordFile::getGasUsed)
                .returns(block2.getHapiVersionMajor(), RecordFile::getHapiVersionMajor)
                .returns(block2.getHapiVersionMinor(), RecordFile::getHapiVersionMinor)
                .returns(block2.getHapiVersionPatch(), RecordFile::getHapiVersionPatch)
                .returns(block2.getHash(), RecordFile::getHash)
                .returns(NUMBER, RecordFile::getIndex)
                .returns(block2.getLoadEnd(), RecordFile::getLoadEnd)
                .returns(block1.getLoadStart(), RecordFile::getLoadStart)
                .returns(block2.getLogsBloom(), RecordFile::getLogsBloom)
                .returns(block1.getName(), RecordFile::getName)
                .returns(block1.getPreviousHash(), RecordFile::getPreviousHash)
                .returns(block1.getSidecarCount() + block2.getSidecarCount(), RecordFile::getSidecarCount)
                .returns(block1.getSize() + block2.getSize(), RecordFile::getSize)
                .returns(block2.getVersion(), RecordFile::getVersion);

        assertThat(transactionRepository.findAll())
                .hasSize(4)
                .extracting(Transaction::getIndex)
                .containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void idempotence() throws Exception {
        mainnet();
        mainnet();
    }

    private RecordFile block(long timestamp) {
        return domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(timestamp)
                        .consensusStart(timestamp - 1)
                        .count(2L)
                        .index(NUMBER)
                        .hapiVersionMinor((int) domainBuilder.number()))
                .persist();
    }

    private Transaction transaction(long timestamp, int index) {
        return domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).index(index).itemizedTransfer(null))
                .persist();
    }
}
