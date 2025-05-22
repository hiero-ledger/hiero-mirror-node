// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.repository.TransactionHashRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfV1
@RequiredArgsConstructor
class TransactionHashTxManagerTest extends ImporterIntegrationTest {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final TransactionHashTxManager transactionHashTxManager;
    private final TransactionHashRepository repository;
    private final DomainBuilder domainBuilder;

    @Test
    @SneakyThrows
    void testThreadCommit() {
        var hash1 = domainBuilder.transactionHash().get();
        hash1.getHash()[0] = 0x01;

        var hash2 = domainBuilder.transactionHash().get();
        hash2.getHash()[0] = 0x02;

        var thread1 = new Thread(() -> {
            var threadState = transactionHashTxManager.updateAndGetThreadState(hash1.calculateV1Shard());
            assertThat(threadState.getStatus()).isEqualTo(-1);
            assertThat(threadState.getProcessedShards()).containsExactly(1);
            TestUtils.insertIntoTransactionHash(jdbcTemplate, hash1);

            var threadState2 = transactionHashTxManager.updateAndGetThreadState(2);
            assertThat(threadState2).isEqualTo(threadState);
            assertThat(threadState.getProcessedShards()).containsExactly(1, 2);
            assertThat(threadState2.getStatus()).isEqualTo(-1);
            TestUtils.insertIntoTransactionHash(jdbcTemplate, hash2);
        });

        var hash3 = domainBuilder.transactionHash().get();
        hash3.getHash()[0] = 0x03;
        var hash4 = domainBuilder.transactionHash().get();
        hash4.getHash()[0] = 0x04;

        var thread2 = new Thread(() -> {
            var threadState = transactionHashTxManager.updateAndGetThreadState(3);
            assertThat(threadState.getStatus()).isEqualTo(-1);
            assertThat(threadState.getProcessedShards()).containsExactly(3);
            TestUtils.insertIntoTransactionHash(jdbcTemplate, hash3);

            var threadState2 = transactionHashTxManager.updateAndGetThreadState(4);
            assertThat(threadState2).isEqualTo(threadState);
            assertThat(threadState.getProcessedShards()).containsExactly(3, 4);
            assertThat(threadState.getStatus()).isEqualTo(-1);
            TestUtils.insertIntoTransactionHash(jdbcTemplate, hash4);
        });

        transactionTemplate.executeWithoutResult(status -> {
            try {
                transactionHashTxManager.initialize(Collections.singleton(hash1), "transaction_hash");
                assertThat(transactionHashTxManager.getItemCount()).isEqualTo(1);
                thread1.start();
                thread1.join();

                assertThat(transactionHashTxManager.getThreadConnections()).hasSize(1);

                transactionHashTxManager.initialize(Collections.singleton(hash2), "transaction_hash");
                assertThat(transactionHashTxManager.getItemCount()).isEqualTo(2);
                thread2.start();
                thread2.join();
                assertThat(transactionHashTxManager.getThreadConnections()).hasSize(2);
                assertThat(repository.findAll()).isEmpty();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(repository.findAll()).containsExactlyInAnyOrder(hash1, hash2, hash3, hash4);
        assertThat(transactionHashTxManager.getThreadConnections()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @SneakyThrows
    void testCompletionStatus(int status) {
        var threadState = new TransactionHashTxManager.ThreadState(mock(Connection.class));
        var threadState2 = new TransactionHashTxManager.ThreadState(mock(Connection.class));
        var threadState3 = new TransactionHashTxManager.ThreadState(mock(Connection.class));
        var errorThreadState = new TransactionHashTxManager.ThreadState(mock(Connection.class));

        transactionHashTxManager.getThreadConnections().put(UUID.randomUUID().toString(), threadState);
        transactionHashTxManager.getThreadConnections().put(UUID.randomUUID().toString(), threadState2);
        transactionHashTxManager.getThreadConnections().put(UUID.randomUUID().toString(), threadState3);
        transactionHashTxManager.getThreadConnections().put(UUID.randomUUID().toString(), errorThreadState);

        doThrow(new RuntimeException("Error thread"))
                .when(errorThreadState.getConnection())
                .commit();
        doThrow(new RuntimeException("Error thread"))
                .when(errorThreadState.getConnection())
                .rollback();

        transactionHashTxManager.afterCompletion(status);
        Arrays.asList(threadState, threadState2, threadState3)
                .forEach(state -> assertThreadState(state, status, state.getStatus()));
        assertThreadState(errorThreadState, status, Integer.MAX_VALUE);
    }

    @SneakyThrows
    void assertThreadState(TransactionHashTxManager.ThreadState threadState, int triedStatus, int outcomeStatus) {
        assertThat(threadState.getStatus()).isEqualTo(outcomeStatus);

        if (triedStatus == STATUS_COMMITTED) {
            verify(threadState.getConnection(), times(1)).commit();
        } else {
            verify(threadState.getConnection(), times(1)).rollback();
        }
    }
}
