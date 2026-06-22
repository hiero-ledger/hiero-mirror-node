// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb;

import static org.hiero.mirror.importer.test.verification.wrb.config.DataSourceContextHolder.RECORDSTREAM;
import static org.hiero.mirror.importer.test.verification.wrb.config.DataSourceContextHolder.WRB;

import com.google.common.collect.Lists;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.SoftAssertions;
import org.hiero.mirror.common.CommonConfiguration;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.repository.ContractRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.hiero.mirror.importer.test.verification.wrb.config.DataSourceConfig;
import org.hiero.mirror.importer.test.verification.wrb.config.DataSourceContextHolder;
import org.hiero.mirror.importer.test.verification.wrb.repository.ContractActionVerificationRepository;
import org.hiero.mirror.importer.test.verification.wrb.repository.ContractStateChangeVerificationRepository;
import org.hiero.mirror.importer.test.verification.wrb.repository.RecordFileVerificationRepository;
import org.hiero.mirror.importer.test.verification.wrb.repository.SidecarFileVerificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.transaction.support.TransactionOperations;

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
        final long consensusEnd = Stream.of(RECORDSTREAM, WRB)
                .map(dataSource -> withDataSource(dataSource, recordFileRepository::findLatest))
                .map(Optional::orElseThrow)
                .map(RecordFile::getConsensusEnd)
                .min(Long::compareTo)
                .orElseThrow();
        log.info("Verifying data up to consensus timestamp: {}", consensusEnd);

        SoftAssertions.assertSoftly(softly -> {
            verifyRecordFiles(softly, consensusEnd);
            verifyContractActions(softly, consensusEnd);
            verifyContracts(softly);
            verifyContractStateChanges(softly, consensusEnd);
            verifySidecarFiles(softly, consensusEnd);
            verifyTransactions(softly, consensusEnd);
        });
    }

    private void verifyRecordFiles(final SoftAssertions softly, final long maxConsensusEnd) {
        final var recordStream = withDataSource(
                RECORDSTREAM,
                () -> recordFileRepository.findAllByConsensusEndLessThanEqualOrderByConsensusEndAsc(maxConsensusEnd));
        final var wrb = withDataSource(
                WRB,
                () -> recordFileRepository.findAllByConsensusEndLessThanEqualOrderByConsensusEndAsc(maxConsensusEnd));
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
                .ignoringFields(
                        "loadEnd",
                        "loadStart",
                        "name",
                        "previousWrappedRecordBlockHash",
                        "size",
                        "wrappedRecordBlockHash")
                .isEqualTo(wrb);
    }

    private void verifyContractActions(final SoftAssertions softly, final long maxConsensusEnd) {
        final var recordStream = withDataSource(
                RECORDSTREAM,
                () ->
                        contractActionRepository
                                .findAllByConsensusTimestampLessThanEqualOrderByConsensusTimestampAscIndexAsc(
                                        maxConsensusEnd));
        final var wrb = withDataSource(
                WRB,
                () ->
                        contractActionRepository
                                .findAllByConsensusTimestampLessThanEqualOrderByConsensusTimestampAscIndexAsc(
                                        maxConsensusEnd));
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
        final var recordStream = withDataSource(
                RECORDSTREAM,
                () ->
                        contractStateChangeRepository
                                .findAllByConsensusTimestampLessThanEqualOrderByConsensusTimestampAscContractIdAscSlotAsc(
                                        maxConsensusEnd));
        final var wrb = withDataSource(
                WRB,
                () ->
                        contractStateChangeRepository
                                .findAllByConsensusTimestampLessThanEqualOrderByConsensusTimestampAscContractIdAscSlotAsc(
                                        maxConsensusEnd));
        log.info("Comparing {} contract_state_change rows", recordStream.size());
        softly.assertThat(recordStream).usingRecursiveComparison().isEqualTo(wrb);
    }

    private void verifySidecarFiles(final SoftAssertions softly, final long maxConsensusEnd) {
        final var recordStream = withDataSource(
                RECORDSTREAM,
                () -> sidecarFileRepository.findAllByConsensusEndLessThanEqualOrderByConsensusEndAscIndexAsc(
                        maxConsensusEnd));
        final var wrb = withDataSource(
                WRB,
                () -> sidecarFileRepository.findAllByConsensusEndLessThanEqualOrderByConsensusEndAscIndexAsc(
                        maxConsensusEnd));
        log.info("Comparing {} sidecar_file rows", recordStream.size());
        softly.assertThat(recordStream)
                .usingRecursiveComparison()
                .ignoringFields("name", "size")
                .isEqualTo(wrb);
    }

    private void verifyTransactions(final SoftAssertions softly, final long maxConsensusEnd) {
        final var pageable = Pageable.unpaged(Sort.by("consensusTimestamp").ascending());
        final var recordStream = withDataSource(
                RECORDSTREAM,
                () -> transactionRepository.findByConsensusTimestampBetween(0, maxConsensusEnd, pageable));
        final var wrb = withDataSource(
                WRB, () -> transactionRepository.findByConsensusTimestampBetween(0, maxConsensusEnd, pageable));
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

    @TestConfiguration
    static class WrbTestConfiguration {

        @Bean
        DomainBuilder domainBuilder(
                final CommonProperties commonProperties,
                final EntityManager entityManager,
                final TransactionOperations transactionOperations) {
            return new DomainBuilder(commonProperties, entityManager, transactionOperations);
        }

        @Bean
        RecordItemBuilder recordItemBuilder(final CommonProperties commonProperties, final SystemEntity systemEntity) {
            return new RecordItemBuilder(commonProperties, systemEntity);
        }
    }
}
