// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.converter.SimpleArgumentConverter;
import org.junit.jupiter.params.provider.CsvSource;

final class LatencyTest {

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            '', -9223372036854775808
            '1,2', 1
            '1,2,3', 2
            '1,2,3,4,5', 3
            '1,2,3,4,5,6', 4
            '1,1,1,5,5,5,5,5', 5
            '1,1,1,1,1,1,1,1,2,3,4,5', 3
            """)
    void average(@ConvertWith(LongListConverter.class) List<Long> history, long expected) {
        // given
        var latency = new Latency();
        history.forEach(latency::record);

        // when, then
        assertThat(latency.getAverage()).isEqualTo(expected);
    }

    private static class LongListConverter extends SimpleArgumentConverter {

        @Override
        protected Object convert(Object source, Class<?> targetType) throws ArgumentConversionException {
            if (source == null) {
                return Collections.emptyList();
            }

            if (source instanceof String input) {
                return Arrays.stream(StringUtils.split(input, ','))
                        .map(Long::valueOf)
                        .toList();
            }

            return Collections.emptyList();
        }
    }
}
