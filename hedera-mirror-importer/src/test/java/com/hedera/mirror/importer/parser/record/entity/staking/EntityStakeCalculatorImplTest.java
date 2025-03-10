// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity.staking;

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

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.entity.ImmutableAccount;
import com.hedera.mirror.importer.repository.EntityStakeRepository;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.awaitility.Durations;
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

    private static final long ENTITY_STAKE_ID = ImmutableAccount.ENTITY_STAKE.getNum();

    private EntityProperties entityProperties;

    @Mock(strictness = LENIENT)
    private EntityStakeRepository entityStakeRepository;

    private EntityStakeCalculatorImpl entityStakeCalculator;
    private CommonProperties commonProperties;

    @BeforeEach
    void setup() {
        commonProperties = new CommonProperties();
        entityProperties = new EntityProperties();
        entityStakeCalculator = new EntityStakeCalculatorImpl(
                entityProperties, entityStakeRepository, TransactionOperations.withoutTransaction(), commonProperties);
        when(entityStakeRepository.updated(anyLong())).thenReturn(false, true);
        when(entityStakeRepository.getEndStakePeriod(anyLong()))
                .thenReturn(Optional.of(100L))
                .thenReturn(Optional.of(101L));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            100, 101
            100, 102
            , 101
            """)
    void calculate(Long endStakePeriodBefore, Long endStakePeriodAfter) {
        when(entityStakeRepository.getEndStakePeriod(ENTITY_STAKE_ID))
                .thenReturn(Optional.ofNullable(endStakePeriodBefore))
                .thenReturn(Optional.of(endStakePeriodAfter));
        var inorder = inOrder(entityStakeRepository);
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).getEndStakePeriod(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).updateEntityStake(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).getEndStakePeriod(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).updated(ENTITY_STAKE_ID);
        inorder.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            100, 101
            100, 102
            , 101
            """)
    void calculateNonZeroShardRealm(Long endStakePeriodBefore, Long endStakePeriodAfter) {
        commonProperties.setShard(1);
        commonProperties.setRealm(1);

        var entityStakeId = EntityId.of(1, 1, ENTITY_STAKE_ID).getId();

        when(entityStakeRepository.getEndStakePeriod(entityStakeId))
                .thenReturn(Optional.ofNullable(endStakePeriodBefore))
                .thenReturn(Optional.of(endStakePeriodAfter));
        var inorder = inOrder(entityStakeRepository);

        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated(entityStakeId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(entityStakeId);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(entityStakeId);
        inorder.verify(entityStakeRepository).updateEntityStake(entityStakeId);
        inorder.verify(entityStakeRepository).getEndStakePeriod(entityStakeId);
        inorder.verify(entityStakeRepository).updated(entityStakeId);
        inorder.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @CsvSource({"99", "100", ","})
    @Timeout(5)
    void calculateWhenEndStakePeriodAfterIsIncorrect(Long endStakePeriodAfter) {
        when(entityStakeRepository.getEndStakePeriod(ENTITY_STAKE_ID))
                .thenReturn(Optional.of(100L))
                .thenReturn(Optional.ofNullable(endStakePeriodAfter));
        var inorder = inOrder(entityStakeRepository);
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).getEndStakePeriod(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).updateEntityStake(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).getEndStakePeriod(ENTITY_STAKE_ID);
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
        when(entityStakeRepository.updated(ENTITY_STAKE_ID)).thenReturn(true);
        entityStakeCalculator.calculate();
        verify(entityStakeRepository).updated(ENTITY_STAKE_ID);
        verify(entityStakeRepository, never()).getEndStakePeriod(ENTITY_STAKE_ID);
        verify(entityStakeRepository, never()).lockFromConcurrentUpdates();
        verify(entityStakeRepository, never()).createEntityStateStart(ENTITY_STAKE_ID);
        verify(entityStakeRepository, never()).updateEntityStake(ENTITY_STAKE_ID);
    }

    @Test
    void calculateWhenExceptionThrown() {
        when(entityStakeRepository.updated(ENTITY_STAKE_ID)).thenThrow(new RuntimeException());
        assertThrows(RuntimeException.class, () -> entityStakeCalculator.calculate());
        verify(entityStakeRepository).updated(ENTITY_STAKE_ID);
        verify(entityStakeRepository, never()).lockFromConcurrentUpdates();
        verify(entityStakeRepository, never()).createEntityStateStart(ENTITY_STAKE_ID);
        verify(entityStakeRepository, never()).updateEntityStake(ENTITY_STAKE_ID);
        verify(entityStakeRepository, never()).getEndStakePeriod(ENTITY_STAKE_ID);

        // calculate again
        reset(entityStakeRepository);
        var inorder = inOrder(entityStakeRepository);
        when(entityStakeRepository.updated(ENTITY_STAKE_ID)).thenReturn(false, true);
        when(entityStakeRepository.getEndStakePeriod(ENTITY_STAKE_ID))
                .thenReturn(Optional.of(100L))
                .thenReturn(Optional.of(101L));
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).updateEntityStake(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).getEndStakePeriod(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).updated(ENTITY_STAKE_ID);
        inorder.verifyNoMoreInteractions();
    }

    @Test
    void concurrentCalculate() {
        // given
        var pool = Executors.newFixedThreadPool(2);
        var semaphore = new Semaphore(0);
        when(entityStakeRepository.updated(ENTITY_STAKE_ID))
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
        inorder.verify(entityStakeRepository).updated(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).getEndStakePeriod(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).updateEntityStake(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).getEndStakePeriod(ENTITY_STAKE_ID);
        inorder.verify(entityStakeRepository).updated(ENTITY_STAKE_ID);
        inorder.verifyNoMoreInteractions();
        pool.shutdown();
    }
}
