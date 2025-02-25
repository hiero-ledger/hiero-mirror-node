// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity.staking;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
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

    private EntityProperties entityProperties;

    @Mock(strictness = LENIENT)
    private EntityStakeRepository entityStakeRepository;

    private EntityStakeCalculatorImpl entityStakeCalculator;

    @BeforeEach
    void setup() {
        entityProperties = new EntityProperties();
        entityStakeCalculator = new EntityStakeCalculatorImpl(
                entityProperties, entityStakeRepository, TransactionOperations.withoutTransaction());
        when(entityStakeRepository.updated()).thenReturn(false, true);
        when(entityStakeRepository.getEndStakePeriod())
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
        when(entityStakeRepository.getEndStakePeriod())
                .thenReturn(Optional.ofNullable(endStakePeriodBefore))
                .thenReturn(Optional.of(endStakePeriodAfter));
        var inorder = inOrder(entityStakeRepository);
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated();
        inorder.verify(entityStakeRepository).getEndStakePeriod();
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart();
        inorder.verify(entityStakeRepository).updateEntityStake();
        inorder.verify(entityStakeRepository).getEndStakePeriod();
        inorder.verify(entityStakeRepository).updated();
        inorder.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @CsvSource({"99", "100", ","})
    @Timeout(5)
    void calculateWhenEndStakePeriodAfterIsIncorrect(Long endStakePeriodAfter) {
        when(entityStakeRepository.getEndStakePeriod())
                .thenReturn(Optional.of(100L))
                .thenReturn(Optional.ofNullable(endStakePeriodAfter));
        var inorder = inOrder(entityStakeRepository);
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated();
        inorder.verify(entityStakeRepository).getEndStakePeriod();
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart();
        inorder.verify(entityStakeRepository).updateEntityStake();
        inorder.verify(entityStakeRepository).getEndStakePeriod();
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
        when(entityStakeRepository.updated()).thenReturn(true);
        entityStakeCalculator.calculate();
        verify(entityStakeRepository).updated();
        verify(entityStakeRepository, never()).getEndStakePeriod();
        verify(entityStakeRepository, never()).lockFromConcurrentUpdates();
        verify(entityStakeRepository, never()).createEntityStateStart();
        verify(entityStakeRepository, never()).updateEntityStake();
    }

    @Test
    void calculateWhenExceptionThrown() {
        when(entityStakeRepository.updated()).thenThrow(new RuntimeException());
        assertThrows(RuntimeException.class, () -> entityStakeCalculator.calculate());
        verify(entityStakeRepository).updated();
        verify(entityStakeRepository, never()).lockFromConcurrentUpdates();
        verify(entityStakeRepository, never()).createEntityStateStart();
        verify(entityStakeRepository, never()).updateEntityStake();
        verify(entityStakeRepository, never()).getEndStakePeriod();

        // calculate again
        reset(entityStakeRepository);
        var inorder = inOrder(entityStakeRepository);
        when(entityStakeRepository.updated()).thenReturn(false, true);
        when(entityStakeRepository.getEndStakePeriod())
                .thenReturn(Optional.of(100L))
                .thenReturn(Optional.of(101L));
        entityStakeCalculator.calculate();
        inorder.verify(entityStakeRepository).updated();
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart();
        inorder.verify(entityStakeRepository).updateEntityStake();
        inorder.verify(entityStakeRepository).getEndStakePeriod();
        inorder.verify(entityStakeRepository).updated();
        inorder.verifyNoMoreInteractions();
    }

    @Test
    void concurrentCalculate() {
        // given
        var pool = Executors.newFixedThreadPool(2);
        var semaphore = new Semaphore(0);
        when(entityStakeRepository.updated())
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
        inorder.verify(entityStakeRepository).updated();
        inorder.verify(entityStakeRepository).getEndStakePeriod();
        inorder.verify(entityStakeRepository).lockFromConcurrentUpdates();
        inorder.verify(entityStakeRepository).createEntityStateStart();
        inorder.verify(entityStakeRepository).updateEntityStake();
        inorder.verify(entityStakeRepository).getEndStakePeriod();
        inorder.verify(entityStakeRepository).updated();
        inorder.verifyNoMoreInteractions();
        pool.shutdown();
    }
}
