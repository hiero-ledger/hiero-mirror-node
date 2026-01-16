// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StringToInstantDeserializerTest {

    private final StringToInstantDeserializer stringToInstantDeserializer = new StringToInstantDeserializer();

    @Mock
    private JsonParser jsonParser;

    @Test
    void deserialize() throws Exception {
        Instant now = Instant.now();
        when(jsonParser.getValueAsString()).thenReturn(now.getEpochSecond() + "." + now.getNano());
        Instant instant = stringToInstantDeserializer.deserialize(jsonParser, context());
        assertThat(instant).isNotNull().isEqualTo(now);
    }

    @Test
    void deserializeNull() throws Exception {
        when(jsonParser.getValueAsString()).thenReturn(null);
        Instant instant = stringToInstantDeserializer.deserialize(jsonParser, context());
        assertThat(instant).isNull();
    }

    @DisplayName("Deserialize String to Instant")
    @ParameterizedTest(name = "with {0}")
    @ValueSource(strings = {"", "foo.bar", "1"})
    void deserializeInvalid(String input) throws Exception {
        when(jsonParser.getValueAsString()).thenReturn(input);
        Instant instant = stringToInstantDeserializer.deserialize(jsonParser, context());
        assertThat(instant).isNull();
    }

    private DeserializationContext context() {
        return new ObjectMapper().getDeserializationContext();
    }
}
