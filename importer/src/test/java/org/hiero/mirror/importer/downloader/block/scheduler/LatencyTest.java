// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.assertj.core.data.Offset;
import org.hiero.mirror.importer.util.LongListConverter;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

@NullUnmarked
final class LatencyTest {

    private static final Offset<Double> OFFSET = Offset.offset(0.1);

    @ParameterizedTest
    @CsvSource(textBlock = """
            '', 0
            '1,2', 1.3
            '1,2,3', 1.8
            '1,2,3,4', 2.5
            '1,2,3,4,5', 3.2
            '1,2,3,4,5,6', 4.0
            '1,1,1,5,5,5,5,5', 4.3
            '1,1,1,1,1,1,1,1,2,3,4,5', 3.2
            """)
    void average(@ConvertWith(LongListConverter.class) List<Long> history, double expected) {
        // given
        var latency = new Latency();
        history.forEach(latency::record);

        // when, then
        assertThat(latency.getAverage()).isCloseTo(expected, OFFSET);
    }

    @Test
    void stale() {
        // given
        final var latency = new Latency();

        // when
        latency.record(10);

        // then
        assertThat(latency.getAverage()).isCloseTo(10.0, OFFSET);

        // when
        latency.markStale();

        // then
        assertThat(latency.getAverage()).isCloseTo(Double.MAX_VALUE, OFFSET);

        // when
        latency.record(20);

        // then
        assertThat(latency.getAverage()).isCloseTo(13, OFFSET);
    }
}
