// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.hiero.mirror.common.converter.ListToStringSerializer.INSTANCE;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListToStringSerializerTest {

    @Mock
    private JsonGenerator jsonGenerator;

    @Test
    void testNull() throws IOException {
        // when
        INSTANCE.serialize(null, jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator, Mockito.never()).writeString(ArgumentMatchers.anyString());
    }

    @Test
    void testEmptyList() throws IOException {
        // when
        INSTANCE.serialize(Collections.emptyList(), jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator, Mockito.times(1)).writeString("{}");
    }

    @Test
    void testNonEmptyList() throws IOException {
        // when
        INSTANCE.serialize(List.of(1L, 2L), jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator, Mockito.times(1)).writeString("{1,2}");
    }
}
