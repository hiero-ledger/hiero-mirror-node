// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.staking;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.awaitility.Durations;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.EntityStakeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class EntityStakeCalculatorImplTest {

    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();

    private EntityProperties entityProperties;

    @Mock(strictness = LENIENT)
    private EntityStakeRepository entityStakeRepository;

    private SystemEntity systemEntity;

    private EntityStakeCalculatorImpl entityStakeCalculator;

    private long stakingRewardAccountId;

    @BeforeEach
    void setup() {
        systemEntity = new SystemEntity(COMMON_PROPERTIES);
        entityProperties = new EntityProperties(systemEntity);
        entityStakeCalculator = new EntityStakeCalculatorImpl(
                entityProperties, entityStakeRepository, TransactionOperations.withoutTransaction(), systemEntity);

        stakingRewardAccountId = systemEntity.stakingRewardAccount().getId();
        when(entityStakeRepository.updated(anyLong())).thenReturn(false, true);
        when(entityStakeRepository.getEndStakePeriod(anyLong()))
                .thenReturn(Optional.of(100L))
                .thenReturn(Optional.of(101L));
        when(entityStakeRepository.getNextEndStakePeriod(anyLong())).thenReturn(Optional.of(101L));
        when(entityStakeRepository.getChunkUpperBoundEntityId(anyLong(), anyLong(), anyLong(), anyInt()))
                .thenReturn(Optional.of(10L))
                .thenReturn(Optional.empty());
        when(entityStakeRepository.getLastProcessedEntityId(anyLong())).thenReturn(Optional.empty());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            100, 101, 0, 0
            100, 102, 0, 0
            , 101, 0, 0
            100, 101, 1, 1
            100, 102, 1, 1
            , 101, 1, 1
            """)
    void calculate(Long endStakePeriodBefore, Long endStakePeriodAfter, long shard, long realm) {
        COMMON_PROPERTIES.setShard(shard);
        COMMON_PROPERTIES.setRealm(realm);
        stakingRewardAccountId = systemEntity.stakingRewardAccount().getId();

        when(entityStakeRepository.getEndStakePeriod(stakingRewardAccountId))
                .thenReturn(Optional.ofNullable(endStakePeriodBefore))
                .thenReturn(Optional.of(endStakePeriodAfter));
        when(entityStakeRepository.getNextEndStakePeriod(stakingRewardAccountId))
                .thenReturn(Optional.of(endStakePeriodAfter));
        var inorder = inOrder(entityStakeRepository);
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getNextEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getLastProcessedEntityId(endStakePeriodAfter);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(stakingRewardAccountId);
        inorder.verify(entityStakeRepository)
                .getChunkUpperBoundEntityId(stakingRewardAccountId, endStakePeriodAfter, 0L, 5000);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository)
                .updateEntityStakeChunk(stakingRewardAccountId, endStakePeriodAfter, 0L, 10L, false);
        inorder.verify(entityStakeRepository).saveProgress(endStakePeriodAfter, 10L, false);
        inorder.verify(entityStakeRepository)
                .getChunkUpperBoundEntityId(stakingRewardAccountId, endStakePeriodAfter, 10L, 5000);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository)
                .updateEntityStakeChunk(stakingRewardAccountId, endStakePeriodAfter, 0L, 0L, true);
        inorder.verify(entityStakeRepository).saveProgress(endStakePeriodAfter, 10L, true);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).deleteCompletedProgress();
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verifyNoMoreInteractions();

        COMMON_PROPERTIES.setShard(0);
        COMMON_PROPERTIES.setRealm(0);
    }

    @ParameterizedTest
    @CsvSource({"99", "100", ","})
    @Timeout(5)
    void calculateWhenEndStakePeriodAfterIsIncorrect(Long endStakePeriodAfter) {
        when(entityStakeRepository.getEndStakePeriod(stakingRewardAccountId))
                .thenReturn(Optional.of(100L))
                .thenReturn(Optional.ofNullable(endStakePeriodAfter));
        var inorder = inOrder(entityStakeRepository);
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getNextEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getLastProcessedEntityId(101L);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 0L, 5000);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).updateEntityStakeChunk(stakingRewardAccountId, 101L, 0L, 10L, false);
        inorder.verify(entityStakeRepository).saveProgress(101L, 10L, false);
        inorder.verify(entityStakeRepository).getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 10L, 5000);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).updateEntityStakeChunk(stakingRewardAccountId, 101L, 0L, 0L, true);
        inorder.verify(entityStakeRepository).saveProgress(101L, 10L, true);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verifyNoMoreInteractions();
    }

    @Test
    void calculateWhenPendingRewardDisabled() {
        entityProperties.getPersist().setPendingReward(false);
        entityStakeCalculator.calculate();
        verifyNoInteractions(entityStakeRepository);
    }

    @Test
    void calculateWhenUpdated() {
        when(entityStakeRepository.updated(stakingRewardAccountId)).thenReturn(true);
        entityStakeCalculator.calculate();
        verify(entityStakeRepository).updated(stakingRewardAccountId);
        verify(entityStakeRepository, never()).getEndStakePeriod(stakingRewardAccountId);
        verify(entityStakeRepository, never()).lockFromConcurrentUpdates();
        verify(entityStakeRepository, never()).createEntityStateStart(anyLong());
        verify(entityStakeRepository, never())
                .updateEntityStakeChunk(anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void calculateWhenExceptionThrown() {
        when(entityStakeRepository.updated(stakingRewardAccountId)).thenThrow(new RuntimeException());
        assertThrows(RuntimeException.class, () -> entityStakeCalculator.calculate());
        verify(entityStakeRepository).updated(stakingRewardAccountId);
        verify(entityStakeRepository, never()).lockFromConcurrentUpdates();
        verify(entityStakeRepository, never()).createEntityStateStart(anyLong());
        verify(entityStakeRepository, never())
                .updateEntityStakeChunk(anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean());
        verify(entityStakeRepository, never()).getEndStakePeriod(stakingRewardAccountId);

        // calculate again
        reset(entityStakeRepository);
        var inorder = inOrder(entityStakeRepository);
        when(entityStakeRepository.updated(stakingRewardAccountId)).thenReturn(false, true);
        when(entityStakeRepository.getEndStakePeriod(stakingRewardAccountId))
                .thenReturn(Optional.of(100L))
                .thenReturn(Optional.of(101L));
        when(entityStakeRepository.getNextEndStakePeriod(stakingRewardAccountId))
                .thenReturn(Optional.of(101L));
        when(entityStakeRepository.getChunkUpperBoundEntityId(anyLong(), anyLong(), anyLong(), anyInt()))
                .thenReturn(Optional.of(10L))
                .thenReturn(Optional.empty());
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getNextEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getLastProcessedEntityId(101L);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 0L, 5000);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).updateEntityStakeChunk(stakingRewardAccountId, 101L, 0L, 10L, false);
        inorder.verify(entityStakeRepository).saveProgress(101L, 10L, false);
        inorder.verify(entityStakeRepository).getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 10L, 5000);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).updateEntityStakeChunk(stakingRewardAccountId, 101L, 0L, 0L, true);
        inorder.verify(entityStakeRepository).saveProgress(101L, 10L, true);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).deleteCompletedProgress();
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verifyNoMoreInteractions();
    }

    @Test
    void concurrentCalculate() {
        // given
        var pool = Executors.newFixedThreadPool(2);
        var semaphore = new Semaphore(0);
        when(entityStakeRepository.updated(stakingRewardAccountId))
                // block until the other task has completed
                .thenAnswer(invocation -> {
                    semaphore.acquire();
                    return false;
                })
                .thenReturn(true);

        // when
        var task1 = pool.submit(() -> entityStakeCalculator.calculate());
        var task2 = pool.submit(() -> entityStakeCalculator.calculate());

        // then
        // verify that only one task is done
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .atMost(Durations.TWO_SECONDS)
                .until(() -> (task1.isDone() || task2.isDone()) && (task1.isDone() != task2.isDone()));
        // unblock the remaining task
        semaphore.release();

        // verify that both tasks are done
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .atMost(Durations.TWO_SECONDS)
                .until(() -> task1.isDone() && task2.isDone());
        var inorder = inOrder(entityStakeRepository);
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getNextEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getLastProcessedEntityId(101L);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 0L, 5000);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).updateEntityStakeChunk(stakingRewardAccountId, 101L, 0L, 10L, false);
        inorder.verify(entityStakeRepository).saveProgress(101L, 10L, false);
        inorder.verify(entityStakeRepository).getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 10L, 5000);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).updateEntityStakeChunk(stakingRewardAccountId, 101L, 0L, 0L, true);
        inorder.verify(entityStakeRepository).saveProgress(101L, 10L, true);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).deleteCompletedProgress();
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verifyNoMoreInteractions();
        pool.shutdown();
    }

    @Test
    void calculateWhenResumeDisabled() {
        // given
        entityProperties.getPersist().setPendingRewardChunkResume(false);
        var inorder = inOrder(entityStakeRepository);

        // when
        entityStakeCalculator.calculate();

        // then: saveProgress and getLastProcessedEntityId are never called
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getNextEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 0L, 5000);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).updateEntityStakeChunk(stakingRewardAccountId, 101L, 0L, 10L, false);
        inorder.verify(entityStakeRepository).getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 10L, 5000);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).updateEntityStakeChunk(stakingRewardAccountId, 101L, 0L, 0L, true);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).deleteCompletedProgress();
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verifyNoMoreInteractions();
        verify(entityStakeRepository, never()).getLastProcessedEntityId(anyLong());
        verify(entityStakeRepository, never()).saveProgress(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void calculateResumesFromSavedProgress() {
        // given
        entityProperties.getPersist().setPendingRewardChunkResume(true);
        entityProperties.getPersist().setPendingRewardChunkSize(5000);
        when(entityStakeRepository.getEndStakePeriod(stakingRewardAccountId))
                .thenReturn(Optional.of(100L))
                .thenReturn(Optional.of(101L));
        when(entityStakeRepository.getNextEndStakePeriod(stakingRewardAccountId))
                .thenReturn(Optional.of(101L));
        when(entityStakeRepository.getLastProcessedEntityId(101L)).thenReturn(Optional.of(7L));
        when(entityStakeRepository.getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 7L, 5000))
                .thenReturn(Optional.of(10L));
        when(entityStakeRepository.getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 10L, 5000))
                .thenReturn(Optional.empty());

        // when
        var inorder = inOrder(entityStakeRepository);
        entityStakeCalculator.calculate();

        // then: first chunk starts from lastProcessedEntityId=7
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getNextEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getLastProcessedEntityId(101L);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 7L, 5000);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).updateEntityStakeChunk(stakingRewardAccountId, 101L, 7L, 10L, false);
        inorder.verify(entityStakeRepository).saveProgress(101L, 10L, false);
        inorder.verify(entityStakeRepository).getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 10L, 5000);

        // staking reward account is updated last
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).updateEntityStakeChunk(stakingRewardAccountId, 101L, 0L, 0L, true);
        inorder.verify(entityStakeRepository).saveProgress(101L, 10L, true);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).deleteCompletedProgress();
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verifyNoMoreInteractions();
    }

    @Test
    @Timeout(5)
    void calculateSleepsBetweenChunksWhenConfigured() {
        // given: 2 chunks + final staking reward account update, with a small delay
        entityProperties.getPersist().setPendingRewardChunkDelay(Duration.ofMillis(50));
        entityProperties.getPersist().setPendingRewardChunkSize(5000);

        when(entityStakeRepository.getEndStakePeriod(stakingRewardAccountId))
                .thenReturn(Optional.of(100L))
                .thenReturn(Optional.of(101L));
        when(entityStakeRepository.getNextEndStakePeriod(stakingRewardAccountId))
                .thenReturn(Optional.of(101L));
        when(entityStakeRepository.getLastProcessedEntityId(101L)).thenReturn(Optional.empty());

        when(entityStakeRepository.getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 0L, 5000))
                .thenReturn(Optional.of(10L));
        when(entityStakeRepository.getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 10L, 5000))
                .thenReturn(Optional.of(20L));
        when(entityStakeRepository.getChunkUpperBoundEntityId(stakingRewardAccountId, 101L, 20L, 5000))
                .thenReturn(Optional.empty());

        // when
        long start = System.currentTimeMillis();
        entityStakeCalculator.calculate();
        long elapsed = System.currentTimeMillis() - start;

        // then: at least one sleep should have occurred between chunks (2 chunks => 2 sleeps in implementation)
        // Use a conservative lower bound to avoid flakiness.
        org.assertj.core.api.Assertions.assertThat(elapsed).isGreaterThanOrEqualTo(50L);
    }
}
