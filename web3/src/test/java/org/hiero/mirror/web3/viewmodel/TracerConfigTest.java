// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TracerConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"PT5S", "5s", "30s", "PT30S"})
    void deserializesIsoAndSpringShorthand(final String timeout) throws Exception {
        final var json = "{\"timeout\":\"%s\"}".formatted(timeout);

        final var config = objectMapper.readValue(json, TracerConfig.class);

        assertThat(config.parsedTimeout()).isPositive();
    }

    @Test
    void deserializesOnlyTopCallAlias() throws Exception {
        final var config =
                objectMapper.readValue("{\"only_top_call\":true,\"tracer\":\"callTracer\"}", TracerConfig.class);

        assertThat(config.isOnlyTopCall()).isTrue();
        assertThat(config.effectiveTracerType()).isEqualTo(TracerType.ACTION);
    }

    @Test
    void rejectsInvalidDuration() throws Exception {
        final var config = objectMapper.readValue("{\"timeout\":\"not-a-duration\"}", TracerConfig.class);

        assertThatThrownBy(config::parsedTimeout).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesFiveSeconds() throws Exception {
        final var iso = objectMapper.readValue("{\"timeout\":\"PT5S\"}", TracerConfig.class);
        final var simple = objectMapper.readValue("{\"timeout\":\"5s\"}", TracerConfig.class);

        assertThat(iso.parsedTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(simple.parsedTimeout()).isEqualTo(Duration.ofSeconds(5));
    }
}
