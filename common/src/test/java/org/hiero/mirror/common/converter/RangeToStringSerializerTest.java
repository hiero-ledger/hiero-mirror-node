// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.hiero.mirror.common.converter.RangeToStringSerializer.INSTANCE;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RangeToStringSerializerTest {

    @Mock
    private JsonGenerator jsonGenerator;

    @ParameterizedTest
    @ValueSource(strings = {"[0,1]", "[0,1)", "(0,1]", "(0,1)", "[0,)", "(,1]", "empty"})
    void serialize(String text) throws IOException {
        Range<Long> range = PostgreSQLGuavaRangeType.longRange(text);
        INSTANCE.serialize(range, jsonGenerator, null);
        Mockito.verify(jsonGenerator).writeString(text);
    }

    @Test
    void serializeNull() throws IOException {
        INSTANCE.serialize(null, jsonGenerator, null);
        Mockito.verify(jsonGenerator, Mockito.never()).writeString(ArgumentMatchers.anyString());
    }
}
