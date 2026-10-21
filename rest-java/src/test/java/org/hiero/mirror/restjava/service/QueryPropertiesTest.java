// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class QueryPropertiesTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void invalidMaxClprChannelsTimestampRange(long nanos) {
        final var properties = new QueryProperties();
        properties.setMaxClprChannelsTimestampRange(Duration.ofNanos(nanos));

        assertThat(VALIDATOR.validate(properties)).hasSize(1);
    }
}
