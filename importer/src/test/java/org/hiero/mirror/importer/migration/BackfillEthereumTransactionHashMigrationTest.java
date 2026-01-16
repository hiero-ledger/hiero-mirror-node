// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.hiero.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1_CALL_DATA_OFFLOADED;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.loadEthereumTransactions;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.populateFileData;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.TransactionHash;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.hiero.mirror.importer.repository.ContractTransactionHashRepository;
import org.hiero.mirror.importer.repository.EthereumTransactionRepository;
import org.hiero.mirror.importer.repository.TransactionHashRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
@Tag("migration")
final class BackfillEthereumTransactionHashMigrationTest extends ImporterIntegrationTest {

    private final ContractResultRepository contractResultRepository;
    private final ContractTransactionHashRepository contractTransactionHashRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final EntityProperties entityProperties;
    private final BackfillEthereumTransactionHashMigration migration;
    private final TransactionHashRepository transactionHashRepository;

    @AfterEach
    void teardown() {
        entityProperties.getPersist().setTransactionHash(true);
    }

    @Test
    void empty() {
        runMigration();
        softly.assertThat(contractResultRepository.findAll()).isEmpty();
        softly.assertThat(contractTransactionHashRepository.findAll()).isEmpty();
        softly.assertThat(ethereumTransactionRepository.findAll()).isEmpty();
        softly.assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void migrate(boolean persistTransactionHash) {
        // given
        entityProperties.getPersist().setTransactionHash(persistTransactionHash);
        populateFileData(jdbcOperations);
        var ethereumTransactions = loadEthereumTransactions();
        ethereumTransactions.forEach(t -> t.setHash(EMPTY_BYTE_ARRAY));
        ethereumTransactionRepository.saveAll(ethereumTransactions);
        var expectedEthereumTransactions = loadEthereumTransactions();

        // Add one with missing file so the service cannot calculate its hash
        var ethereumTransaction = domainBuilder
                .ethereumTransaction(false)
                .customize(t -> t.data(RAW_TX_TYPE_1_CALL_DATA_OFFLOADED).hash(EMPTY_BYTE_ARRAY))
                .persist();
        expectedEthereumTransactions.add(ethereumTransaction);

        // Add one with non-empty hash
        expectedEthereumTransactions.add(domainBuilder.ethereumTransaction(true).persist());

        // Append data to one of the call data files to verify the migration is using historical file state
        ethereumTransactions.stream()
                .filter(e -> ArrayUtils.isEmpty(e.getCallData()) && !EntityId.isEmpty(e.getCallDataId()))
                .findFirst()
                .ifPresent(e -> domainBuilder
                        .fileData()
                        .customize(f -> f.consensusTimestamp(
                                        ethereumTransactions.getLast().getConsensusTimestamp() + 1)
                                .entityId(e.getCallDataId())
                                .transactionType(TransactionType.FILEAPPEND.getProtoId()))
                        .persist());

        var expectedHashes = expectedEthereumTransactions.stream()
                .collect(Collectors.toMap(EthereumTransaction::getConsensusTimestamp, EthereumTransaction::getHash));
        var expectedContractResults = persistContractResults(expectedHashes);
        var expectedContractTransactionHashes = persistContractTransactionHashes(expectedHashes);
        persistTransactionHashes();

        // when
        runMigration();

        // then
        softly.assertThat(contractResultRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedContractResults);
        softly.assertThat(contractTransactionHashRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedContractTransactionHashes);
        softly.assertThat(ethereumTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEthereumTransactions);

        if (persistTransactionHash) {
            var expectedTransactionHashes = expectedEthereumTransactions.stream()
                    .filter(e -> ArrayUtils.isNotEmpty(e.getHash()))
                    .map(e -> TransactionHash.builder()
                            .consensusTimestamp(e.getConsensusTimestamp())
                            .hash(e.getHash())
                            .payerAccountId(e.getPayerAccountId().getId())
                            .build())
                    .toList();
            softly.assertThat(transactionHashRepository.findAll())
                    .containsExactlyInAnyOrderElementsOf(expectedTransactionHashes);
        } else {
            softly.assertThat(transactionHashRepository.findAll()).isEmpty();
        }
    }

    @Test
    void migrateWithMissingCallDataFile() {
        // given
        var ethereumTransaction = domainBuilder
                .ethereumTransaction(false)
                .customize(t -> t.data(RAW_TX_TYPE_1_CALL_DATA_OFFLOADED).hash(EMPTY_BYTE_ARRAY))
                .persist();
        var expectedContractResults = persistContractResults(Collections.emptyMap());
        var expectedContractTransactionHashes = persistContractTransactionHashes(Collections.emptyMap());

        // when
        runMigration();

        // then
        softly.assertThat(contractResultRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedContractResults);
        softly.assertThat(contractTransactionHashRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedContractTransactionHashes);
        softly.assertThat(ethereumTransactionRepository.findAll()).containsExactly(ethereumTransaction);
        softly.assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    private List<ContractResult> persistContractResults(Map<Long, byte[]> expected) {
        return Lists.newArrayList(ethereumTransactionRepository.findAll()).stream()
                .map(e -> {
                    // Create and persist contract result from ethereum transactions in database so
                    // the hash column is consistent
                    var contractResult = domainBuilder
                            .contractResult()
                            .customize(cr -> cr.consensusTimestamp(e.getConsensusTimestamp())
                                    .transactionHash(e.getHash())
                                    .payerAccountId(e.getPayerAccountId()))
                            .persist();
                    // Set hash to the expected value
                    contractResult.setTransactionHash(
                            expected.getOrDefault(e.getConsensusTimestamp(), EMPTY_BYTE_ARRAY));
                    return contractResult;
                })
                .toList();
    }

    private List<ContractTransactionHash> persistContractTransactionHashes(Map<Long, byte[]> expected) {
        return Lists.newArrayList(ethereumTransactionRepository.findAll()).stream()
                .map(e -> {
                    // Create and persist contract transaction hash from ethereum transactions in database so
                    // the hash column is consistent
                    var contractTransactionHash = domainBuilder
                            .contractTransactionHash()
                            .customize(cr -> cr.consensusTimestamp(e.getConsensusTimestamp())
                                    .hash(e.getHash())
                                    .payerAccountId(e.getPayerAccountId().getId()))
                            .persist();
                    // Set hash to the expected value
                    contractTransactionHash.setHash(expected.getOrDefault(e.getConsensusTimestamp(), EMPTY_BYTE_ARRAY));
                    return contractTransactionHash;
                })
                .toList();
    }

    private void persistTransactionHashes() {
        if (!entityProperties.getPersist().shouldPersistTransactionHash(TransactionType.ETHEREUMTRANSACTION)) {
            return;
        }

        ethereumTransactionRepository.findAll().forEach(e -> {
            if (ArrayUtils.isNotEmpty(e.getHash())) {
                domainBuilder
                        .transactionHash()
                        .customize(t -> t.consensusTimestamp(e.getConsensusTimestamp())
                                .hash(e.getHash())
                                .payerAccountId(e.getPayerAccountId().getId()))
                        .persist();
            }
        });
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }
}
