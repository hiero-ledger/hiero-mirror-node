// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class LatencyServicePropertiesTest {

    @Test
    void defaults() {
        assertThatCode(() -> new LatencyServiceProperties().validate()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(longs = {19, 10})
    void maxBackoff(final long maxBackoffSeconds) {
        // given
        final var properties = new LatencyServiceProperties();
        properties.setMaxBackoff(Duration.ofSeconds(maxBackoffSeconds));

        // when, then
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxBackoff must be at least twice frequency");
    }

    @ParameterizedTest
    @ValueSource(longs = {10, 11})
    void timeout(final long timeoutSeconds) {
        // given
        final var properties = new LatencyServiceProperties();
        properties.setTimeout(Duration.ofSeconds(timeoutSeconds));

        // when, then
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be less than frequency");
    }
}
