// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.staking;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        var inorder = inOrder(entityStakeRepository);
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).updateEntityStake(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
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
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).updateEntityStake(stakingRewardAccountId);
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
        verify(entityStakeRepository, never()).createEntityStateStart(stakingRewardAccountId);
        verify(entityStakeRepository, never()).updateEntityStake(stakingRewardAccountId);
    }

    @Test
    void calculateWhenExceptionThrown() {
        when(entityStakeRepository.updated(stakingRewardAccountId)).thenThrow(new RuntimeException());
        assertThrows(RuntimeException.class, () -> entityStakeCalculator.calculate());
        verify(entityStakeRepository).updated(stakingRewardAccountId);
        verify(entityStakeRepository, never()).lockFromConcurrentUpdates();
        verify(entityStakeRepository, never()).createEntityStateStart(stakingRewardAccountId);
        verify(entityStakeRepository, never()).updateEntityStake(stakingRewardAccountId);
        verify(entityStakeRepository, never()).getEndStakePeriod(stakingRewardAccountId);

        // calculate again
        reset(entityStakeRepository);
        var inorder = inOrder(entityStakeRepository);
        when(entityStakeRepository.updated(stakingRewardAccountId)).thenReturn(false, true);
        when(entityStakeRepository.getEndStakePeriod(stakingRewardAccountId))
                .thenReturn(Optional.of(100L))
                .thenReturn(Optional.of(101L));
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).updateEntityStake(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
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
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).updateEntityStake(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(stakingRewardAccountId);
        inorder.verify(entityStakeRepository).updated(stakingRewardAccountId);
        inorder.verifyNoMoreInteractions();
        pool.shutdown();
    }
}
