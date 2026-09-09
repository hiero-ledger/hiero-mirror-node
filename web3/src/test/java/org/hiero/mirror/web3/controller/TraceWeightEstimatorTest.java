// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

final class TraceWeightEstimatorTest {

    private final TraceWeightEstimator estimator = new TraceWeightEstimator(100, 1_000, 100, 10_000);

    @Test
    void defaultsToStackOnlyWhenNoParamsPresent() {
        // Given
        final var request = new MockHttpServletRequest();

        // When
        final var weight = estimator.estimate(request);

        // Then
        assertThat(weight).isEqualTo(estimator.baseOpcodeBytes() + estimator.stackBytes());
    }

    @Test
    void includesEveryFlagWhenAllRequested() {
        // Given
        final var request = new MockHttpServletRequest();
        request.setParameter("stack", "true");
        request.setParameter("memory", "true");
        request.setParameter("storage", "true");

        // When
        final var weight = estimator.estimate(request);

        // Then
        assertThat(weight)
                .isEqualTo(estimator.baseOpcodeBytes()
                        + estimator.stackBytes()
                        + estimator.memoryBytes()
                        + estimator.storageBytes());
    }

    @Test
    void excludesFlagsExplicitlySetToFalse() {
        // Given
        final var request = new MockHttpServletRequest();
        request.setParameter("stack", "false");
        request.setParameter("memory", "false");
        request.setParameter("storage", "false");

        // When
        final var weight = estimator.estimate(request);

        // Then
        assertThat(weight).isEqualTo(estimator.baseOpcodeBytes());
    }

    @Test
    void treatsUnrecognizedValueAsEnabled() {
        // Given
        final var request = new MockHttpServletRequest();
        request.setParameter("memory", "not-a-boolean");

        // When
        final var weight = estimator.estimate(request);

        // Then
        assertThat(weight).isEqualTo(estimator.baseOpcodeBytes() + estimator.stackBytes() + estimator.memoryBytes());
    }
}
