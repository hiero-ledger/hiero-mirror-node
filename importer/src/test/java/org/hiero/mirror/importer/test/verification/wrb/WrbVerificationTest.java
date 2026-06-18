// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb;

import static org.hiero.mirror.importer.test.verification.wrb.DataSourceContextHolder.RECORDSTREAM;
import static org.hiero.mirror.importer.test.verification.wrb.DataSourceContextHolder.WRB;

import com.google.common.collect.Lists;
import java.util.function.Supplier;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.SoftAssertions;
import org.hiero.mirror.common.CommonConfiguration;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.repository.ContractRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.EnabledIf;

@CustomLog
@EnabledIf(expression = "${WRB_TEST_ENABLED:false}")
@DisableRepeatableSqlMigration
@Import({CommonConfiguration.class, DataSourceConfig.class})
@RequiredArgsConstructor
@SpringBootTest
@TestPropertySource(
        properties = {"hiero.mirror.importer.downloader.bucketName=", "spring.flyway.baselineVersion=2.999.999"})
final class WrbVerificationTest {

    private final ContractActionVerificationRepository contractActionRepository;
    private final ContractRepository contractRepository;
    private final ContractStateChangeVerificationRepository contractStateChangeRepository;
    private final RecordFileVerificationRepository recordFileRepository;
    private final SidecarFileVerificationRepository sidecarFileRepository;
    private final TransactionRepository transactionRepository;

    @Test
    void verify() {
        // Step 1: Use the lower of the two databases' latest consensusEnd as the comparison ceiling
        final long recordStreamMaxConsensusEnd = withDataSource(
                        RECORDSTREAM, () -> recordFileRepository.findLatest().orElseThrow())
                .getConsensusEnd();
        final long wrbMaxConsensusENd = withDataSource(
                        WRB, () -> recordFileRepository.findLatest().orElseThrow())
                .getConsensusEnd();
        long maxConsensusEnd = Math.min(recordStreamMaxConsensusEnd, wrbMaxConsensusENd);
        log.info("Verifying data up to consensusEnd: {}", maxConsensusEnd);

        SoftAssertions.assertSoftly(softly -> {
            verifyRecordFiles(softly, maxConsensusEnd);
            verifyContractActions(softly, maxConsensusEnd);
            verifyContracts(softly);
            verifyContractStateChanges(softly, maxConsensusEnd);
            verifySidecarFiles(softly, maxConsensusEnd);
            verifyTransactions(softly, maxConsensusEnd);
        });
    }

    private void verifyRecordFiles(final SoftAssertions softly, final long maxConsensusEnd) {
        final var recordStream = withDataSource(RECORDSTREAM, () -> recordFileRepository.findUpTo(maxConsensusEnd));
        final var wrb = withDataSource(WRB, () -> recordFileRepository.findUpTo(maxConsensusEnd));
        log.info("Comparing {} record_file rows", recordStream.size());

        softly.assertThat(recordStream).allSatisfy(f -> {
            softly.assertThat(f.getPreviousWrappedRecordBlockHash()).isNull();
            softly.assertThat(f.getWrappedRecordBlockHash()).isNull();
        });

        softly.assertThat(wrb).allSatisfy(f -> {
            softly.assertThat(f.getPreviousWrappedRecordBlockHash()).isNotNull();
            softly.assertThat(f.getWrappedRecordBlockHash()).isNotNull();
        });

        softly.assertThat(recordStream)
                .usingRecursiveComparison()
                .ignoringFields("name", "previousWrappedRecordBlockHash", "wrappedRecordBlockHash")
                .isEqualTo(wrb);
    }

    private void verifyContractActions(final SoftAssertions softly, final long maxConsensusEnd) {
        var recordStream = withDataSource(RECORDSTREAM, () -> contractActionRepository.findUpTo(maxConsensusEnd));
        var wrb = withDataSource(WRB, () -> contractActionRepository.findUpTo(maxConsensusEnd));
        log.info("Comparing {} contract_action rows", recordStream.size());
        softly.assertThat(recordStream).usingRecursiveComparison().isEqualTo(wrb);
    }

    private void verifyContracts(final SoftAssertions softly) {
        final var recordStream = Lists.newLinkedList(withDataSource(RECORDSTREAM, contractRepository::findAll));
        final var wrb = Lists.newLinkedList(withDataSource(WRB, contractRepository::findAll));
        log.info("Comparing {} contract rows", recordStream.size());
        softly.assertThat(recordStream).containsExactlyInAnyOrderElementsOf(wrb);
    }

    private void verifyContractStateChanges(final SoftAssertions softly, final long maxConsensusEnd) {
        var recordStream = withDataSource(RECORDSTREAM, () -> contractStateChangeRepository.findUpTo(maxConsensusEnd));
        var wrb = withDataSource(WRB, () -> contractStateChangeRepository.findUpTo(maxConsensusEnd));
        log.info("Comparing {} contract_state_change rows", recordStream.size());
        softly.assertThat(recordStream).usingRecursiveComparison().isEqualTo(wrb);
    }

    private void verifySidecarFiles(final SoftAssertions softly, final long maxConsensusEnd) {
        var recordStream = withDataSource(RECORDSTREAM, () -> sidecarFileRepository.findUpTo(maxConsensusEnd));
        var wrb = withDataSource(WRB, () -> sidecarFileRepository.findUpTo(maxConsensusEnd));
        log.info("Comparing {} sidecar_file rows", recordStream.size());
        softly.assertThat(recordStream).containsExactlyInAnyOrderElementsOf(wrb);
    }

    private void verifyTransactions(final SoftAssertions softly, final long maxConsensusEnd) {
        var recordStream = withDataSource(
                RECORDSTREAM,
                () -> transactionRepository.findByConsensusTimestampBetween(0, maxConsensusEnd, Pageable.unpaged()));
        var wrb = withDataSource(
                WRB,
                () -> transactionRepository.findByConsensusTimestampBetween(0, maxConsensusEnd, Pageable.unpaged()));
        log.info("Comparing {} transaction rows", recordStream.size());
        softly.assertThat(recordStream)
                .usingRecursiveComparison()
                .comparingOnlyFields("consensusTimestamp", "transactionBytes", "transactionRecordBytes")
                .isEqualTo(wrb);
    }

    private <T> T withDataSource(final String ds, final Supplier<T> supplier) {
        DataSourceContextHolder.set(ds);
        try {
            return supplier.get();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}
