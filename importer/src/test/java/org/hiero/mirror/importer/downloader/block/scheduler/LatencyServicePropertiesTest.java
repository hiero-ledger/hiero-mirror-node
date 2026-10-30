// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class LatencyServicePropertiesTest {

    @AutoClose
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    @Test
    void defaults() {
        assertThat(violationPaths(new LatencyServiceProperties())).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            20, false
            19, true
            10, true
            """)
    void maxBackoff(final long maxBackoffSeconds, final boolean violated) {
        // given
        final var properties = new LatencyServiceProperties();
        properties.setMaxBackoff(Duration.ofSeconds(maxBackoffSeconds));

        // when, then
        assertThat(violationPaths(properties).contains("maxBackoffValid")).isEqualTo(violated);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            9, false
            10, true
            11, true
            """)
    void timeout(final long timeoutSeconds, final boolean violated) {
        // given
        final var properties = new LatencyServiceProperties();
        properties.setTimeout(Duration.ofSeconds(timeoutSeconds));

        // when, then
        assertThat(violationPaths(properties).contains("timeoutValid")).isEqualTo(violated);
    }

    private static Set<String> violationPaths(final LatencyServiceProperties properties) {
        return VALIDATOR_FACTORY.getValidator().validate(properties).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
