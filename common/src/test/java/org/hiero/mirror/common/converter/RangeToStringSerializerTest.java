// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.collect.Range;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RangeToStringSerializerTest {

    private final RangeToStringSerializer serializer = new RangeToStringSerializer();

    @Mock
    private JsonGenerator jsonGenerator;

    static java.util.stream.Stream<Arguments> rangesAndExpectedJsonStrings() {
        return java.util.stream.Stream.of(
                Arguments.of(Range.closedOpen(0L, 1L), "[0,1)"),
                Arguments.of(Range.closed(0L, 1L), "[0,1)"),
                Arguments.of(Range.open(0L, 1L), "[0,1)"),
                Arguments.of(Range.openClosed(0L, 1L), "[0,1)"),
                Arguments.of(Range.closedOpen(0L, 2L), "[0,2)"),
                Arguments.of(Range.atLeast(0L), "[0,)"),
                Arguments.of(Range.lessThan(1L), "[,1)"));
    }

    @ParameterizedTest
    @MethodSource("rangesAndExpectedJsonStrings")
    void serialize(Range<Long> range, String expectedText) throws IOException {
        serializer.serialize(range, jsonGenerator, null);
        verify(jsonGenerator).writeString(expectedText);
    }

    @Test
    void serializeNull() throws IOException {
        serializer.serialize(null, jsonGenerator, null);
        verify(jsonGenerator).writeNull();
        verify(jsonGenerator, never()).writeString(ArgumentMatchers.anyString());
    }

    @Test
    void serializeEmptyRange() throws IOException {
        serializer.serialize(Range.closedOpen(1L, 1L), jsonGenerator, null);
        verify(jsonGenerator).writeNull();
    }
}
