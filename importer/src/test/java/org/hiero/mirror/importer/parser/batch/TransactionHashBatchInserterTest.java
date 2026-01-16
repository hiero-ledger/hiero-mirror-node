// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionHash;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.repository.TransactionHashRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfV1
@RequiredArgsConstructor
class TransactionHashBatchInserterTest extends ImporterIntegrationTest {
    private final JdbcTemplate jdbcTemplate;
    private final BatchPersister batchPersister;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionHashBatchInserter hashBatchInserter;
    private final DataSource datasource;
    private final TransactionHashRepository transactionHashRepository;
    private Set<TransactionHash> transactionHashes;
    private Set<Transaction> transactions;
    private Map<Integer, List<TransactionHash>> shardMap;

    @BeforeEach
    void setup() {
        transactionHashes = transactionHash(64);
        transactions = transactions(10);
        shardMap = transactionHashes.stream().collect(Collectors.groupingBy(TransactionHash::calculateV1Shard));
    }

    @Test
    void persist() {
        // Execute inside a parent transaction
        var threadStates = transactionTemplate.execute(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            batchPersister.persist(transactionHashes);
            // Inside a different transaction so will not be available when queried here
            shardMap.keySet().forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                    .isEmpty());
            assertThat(transactionHashRepository.findAll()).isEmpty();

            return new ConcurrentHashMap<>(hashBatchInserter.getThreadConnections());
        });
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

        // Now that parent transaction has committed, the transactions for threads will be committed
        assertThreadState(STATUS_COMMITTED, threadStates);

        shardMap.forEach((shard, items) -> {
            assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                    .containsExactlyInAnyOrderElementsOf(items);
            items.forEach(hash -> assertThat(TestUtils.getTransactionHashFromSqlFunction(jdbcTemplate, hash.getHash()))
                    .isEqualTo(hash));
        });
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactionHashes);
    }

    @Test
    void persistBatches() {
        var batch2Transactions = transactions(10);
        var batch2Hashes = transactionHash(64);
        var combinedTransactions = new HashSet<>(transactions);
        combinedTransactions.addAll(batch2Transactions);
        var combinedHashes = new HashSet<>(transactionHashes);
        combinedHashes.addAll(batch2Hashes);

        batch2Hashes.stream()
                .collect(Collectors.groupingBy(TransactionHash::calculateV1Shard))
                .forEach((key, value) ->
                        shardMap.computeIfAbsent(key, k -> new ArrayList<>()).addAll(value));

        // Execute inside a parent transaction
        var threadStates = transactionTemplate.execute(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            batchPersister.persist(batch2Transactions);

            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(combinedTransactions);
            batchPersister.persist(transactionHashes);
            batchPersister.persist(batch2Hashes);

            // Inside a different transaction so will not be available when queried here
            shardMap.keySet().forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                    .isEmpty());
            assertThat(transactionHashRepository.findAll()).isEmpty();

            return new ConcurrentHashMap<>(hashBatchInserter.getThreadConnections());
        });
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(combinedTransactions);

        // Now that parent transaction has committed, the transactions for threads will be committed
        assertThreadState(STATUS_COMMITTED, threadStates);
        shardMap.forEach((shard, items) -> {
            assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                    .containsExactlyInAnyOrderElementsOf(items);
            items.forEach(hash -> assertThat(TestUtils.getTransactionHashFromSqlFunction(jdbcTemplate, hash.getHash()))
                    .isEqualTo(hash));
        });
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(combinedHashes);
    }

    @Test
    void persistBatchesConnectionClosed() {
        var batch2Transactions = transactions(10);
        var batch2Hashes = transactionHash(64);
        var combinedTransactions = new HashSet<>(transactions);
        combinedTransactions.addAll(batch2Transactions);
        var combinedHashes = new HashSet<>(transactionHashes);
        combinedHashes.addAll(batch2Hashes);

        batch2Hashes.stream()
                .collect(Collectors.groupingBy(TransactionHash::calculateV1Shard))
                .forEach((key, value) ->
                        shardMap.computeIfAbsent(key, k -> new ArrayList<>()).addAll(value));

        // Execute inside a parent transaction
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    batchPersister.persist(transactions);
                    assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

                    batchPersister.persist(batch2Transactions);

                    assertThat(transactionRepository.findAll())
                            .containsExactlyInAnyOrderElementsOf(combinedTransactions);
                    batchPersister.persist(transactionHashes);

                    // Close Connections
                    hashBatchInserter
                            .getThreadConnections()
                            .values()
                            .forEach(state -> markAndCloseConnection(state.getConnection()));

                    // throws ParserException trying to persist second batch
                    batchPersister.persist(batch2Hashes);
                }))
                .isInstanceOf(ParserException.class);

        assertThat(transactionRepository.findAll()).isEmpty();
        assertThat(transactionHashRepository.findAll()).isEmpty();
        assertThat(hashBatchInserter.getThreadConnections()).isEmpty();
    }

    @Test
    void persistEmpty() {
        // Execute inside a parent transaction
        var threadStates = transactionTemplate.execute(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            hashBatchInserter.persist(Collections.emptyList());
            hashBatchInserter.persist(null);

            return new ConcurrentHashMap<>(hashBatchInserter.getThreadConnections());
        });
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);
        assertThat(transactionHashRepository.findAll()).isEmpty();
        assertThat(threadStates).isEmpty();
    }

    @Test
    void persistParentFails() {
        // Execute inside a parent transaction
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    batchPersister.persist(transactions);
                    assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

                    batchPersister.persist(transactionHashes);
                    shardMap.keySet()
                            .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                                    .isEmpty());

                    throw new RuntimeException("intentional");
                }))
                .hasMessage("intentional");

        assertThat(transactionRepository.findAll()).isEmpty();
        shardMap.keySet().forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                .isEmpty());
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void persistParentTransactionClosedBeforeCommit() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    batchPersister.persist(transactions);
                    assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

                    batchPersister.persist(transactionHashes);
                    shardMap.keySet()
                            .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                                    .isEmpty());

                    // Get holder of parent transaction
                    ConnectionHolder connectionHolder =
                            (ConnectionHolder) TransactionSynchronizationManager.getResource(datasource);
                    markAndCloseConnection(connectionHolder.getConnection());
                }))
                .hasMessageStartingWith("Unable to commit against JDBC Connection");

        assertThat(transactionRepository.findAll()).isEmpty();
        shardMap.keySet().forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                .isEmpty());
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void persistChildTransactionClosedBeforeCommit() {
        // Execute inside a parent transaction
        var threadStates = transactionTemplate.execute(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            batchPersister.persist(transactionHashes);
            shardMap.keySet().forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                    .isEmpty());

            assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
            hashBatchInserter.getThreadConnections().values().forEach(threadState -> assertThat(threadState.getStatus())
                    .isEqualTo(-1));

            // Get connection of thread transaction
            var threadToClose =
                    hashBatchInserter.getThreadConnections().values().iterator().next();
            markAndCloseConnection(threadToClose.getConnection());
            return new ConcurrentHashMap<>(hashBatchInserter.getThreadConnections());
        });
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

        var closedThread = threadStates.entrySet().stream()
                .filter(entry -> Integer.MAX_VALUE == entry.getValue().getStatus())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Should have closed connection"));

        assertThreadState(Integer.MAX_VALUE, closedThread.getValue());
        threadStates.remove(closedThread.getKey());

        closedThread.getValue().getProcessedShards().forEach(shard -> {
            assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate)).isEmpty();
            shardMap.remove(shard);
        });

        assertThreadState(STATUS_COMMITTED, threadStates);
        shardMap.forEach((shard, items) -> {
            assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                    .containsExactlyInAnyOrderElementsOf(items);
            items.forEach(hash -> assertThat(TestUtils.getTransactionHashFromSqlFunction(jdbcTemplate, hash.getHash()))
                    .isEqualTo(hash));
        });

        var expected = transactionHashes.stream()
                .filter(hash -> !closedThread.getValue().getProcessedShards().contains(hash.calculateV1Shard()))
                .toList();
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void persistTransactionHashInvalid(byte[] bytes) {
        // Execute inside a parent transaction
        transactionTemplate.executeWithoutResult(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            transactionHashes.iterator().next().setHash(bytes);
            batchPersister.persist(transactionHashes);
            shardMap.keySet().forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                    .isEmpty());
        });

        shardMap.forEach((shard, items) -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate))
                .isEqualTo(items.stream().filter(TransactionHash::hashIsValid).toList()));
        assertThat(transactionHashRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(transactionHashes.stream()
                        .filter(TransactionHash::hashIsValid)
                        .toList());
    }

    @SneakyThrows
    private void markAndCloseConnection(Connection connection) {
        connection.getMetaData();
        connection.close();
    }

    @SneakyThrows
    private void assertThreadState(int status, TransactionHashTxManager.ThreadState threadState) {
        assertThat(threadState.getStatus()).isEqualTo(status);
        assertThat(threadState.getConnection().isClosed()).isTrue();
    }

    private void assertThreadState(
            int status, ConcurrentHashMap<String, TransactionHashTxManager.ThreadState> threadStates) {
        assertThat(threadStates).isNotEmpty();
        threadStates.values().forEach(threadState -> assertThreadState(status, threadState));
    }

    private Set<TransactionHash> transactionHash(int count) {
        var returnVal = new HashSet<TransactionHash>();
        var consensusTimestamp = Instant.now().toEpochMilli();

        for (int i = 0; i < count; i++) {
            returnVal.add(TransactionHash.builder()
                    .consensusTimestamp(consensusTimestamp * 1_000_000L + i)
                    .payerAccountId(1)
                    .hash(domainBuilder.transactionHash().get().getHash())
                    .build());
        }
        return returnVal;
    }

    private Set<Transaction> transactions(int count) {
        var returnVal = new HashSet<Transaction>();
        for (int i = 0; i < count; i++) {
            returnVal.add(domainBuilder.transaction().get());
        }

        return returnVal;
    }
}
