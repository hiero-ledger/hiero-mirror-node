// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StringToDurationDeserializerTest {

    private final StringToDurationDeserializer deserializer = new StringToDurationDeserializer();

    @Mock
    private JsonParser jsonParser;

    @DisplayName("Convert Duration to String")
    @ParameterizedTest(name = "{0} => {1}")
    @CsvSource({
        ",",
        "0s, 0",
        "1s, 1",
        "1m, 60",
        "1m1s, 61",
        "1h, 3600",
        "1h1m, 3660",
        "1h1m1s, 3661",
        "1d, 86400",
        "1d1h, 90000",
        "1d1h1m, 90060",
        "1d1h1m1s, 90061"
    })
    void deserialize(String input, Long expected) throws Exception {
        when(jsonParser.getValueAsString()).thenReturn(input);
        Duration duration = expected != null ? Duration.ofSeconds(expected) : null;
        var context = new ObjectMapper().getDeserializationContext();
        assertThat(deserializer.deserialize(jsonParser, context)).isEqualTo(duration);
    }
}
