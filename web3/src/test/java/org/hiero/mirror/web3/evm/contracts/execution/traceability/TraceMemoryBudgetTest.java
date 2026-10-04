// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.junit.jupiter.api.Test;

final class TraceMemoryBudgetTest {

    private static TraceMemoryBudget budgetOf(final int maxBytes) {
        final var properties = new OpcodesProperties();
        properties.setMaxConcurrentTraceBytes(maxBytes);
        return new TraceMemoryBudget(properties);
    }

    @Test
    void reservesWithinBudget() {
        // Given
        final var budget = budgetOf(10);

        // When / Then
        assertThat(budget.tryReserve(10)).isTrue();
        assertThat(budget.tryReserve(1)).isFalse();
    }

    @Test
    void rejectsReservationExceedingBudget() {
        // Given
        final var budget = budgetOf(10);

        // When / Then
        assertThat(budget.tryReserve(11)).isFalse();
    }

    @Test
    void releaseFreesReservedBytesForReuse() {
        // Given
        final var budget = budgetOf(10);
        budget.tryReserve(10);

        // When
        budget.release(10);

        // Then
        assertThat(budget.tryReserve(10)).isTrue();
    }

    @Test
    void zeroOrNegativeReservationsAlwaysSucceed() {
        // Given
        final var budget = budgetOf(10);
        budget.tryReserve(10);

        // When / Then
        assertThat(budget.tryReserve(0)).isTrue();
        assertThat(budget.tryReserve(-1)).isTrue();
    }

    @Test
    void unlimitedBudgetNeverRejects() {
        // Given
        final var budget = TraceMemoryBudget.unlimited();

        // When / Then
        assertThat(budget.tryReserve(Integer.MAX_VALUE)).isTrue();
    }
}
