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
    void fresh() {
        // given
        final var latency = new Latency();

        // when
        latency.record(10);

        // then
        assertThat(latency.isFresh(Long.MAX_VALUE)).isTrue();
        assertThat(latency.isFresh(-1)).isFalse();
    }

    @Test
    void getAverageWithMaxAge() throws InterruptedException {
        // given
        final var latency = new Latency();

        // when, then
        assertThat(latency.getAverage(Long.MAX_VALUE)).isEqualTo(Double.MAX_VALUE);

        // when
        latency.record(10);

        // then
        assertThat(latency.getAverage(Long.MAX_VALUE)).isCloseTo(10.0, OFFSET);

        // when the sample becomes older than the max age
        Thread.sleep(20);

        // then
        assertThat(latency.getAverage(10)).isEqualTo(Double.MAX_VALUE);
        assertThat(latency.getAverage()).isCloseTo(10.0, OFFSET);
    }

    @Test
    void lastMeasuredMillis() {
        // given
        final var latency = new Latency();
        assertThat(latency.getLastMeasuredMillis()).isZero();
        final long before = System.currentTimeMillis();

        // when
        latency.record(10);

        // then
        assertThat(latency.getLastMeasuredMillis()).isBetween(before, System.currentTimeMillis());
    }

    @Test
    void metricValue() {
        // given
        final var latency = new Latency();

        // when, then
        assertThat(latency.getMetricValue()).isNaN();

        // when
        latency.record(10);

        // then
        assertThat(latency.getMetricValue()).isCloseTo(10.0, OFFSET);

        // when
        latency.markStale();

        // then
        assertThat(latency.getMetricValue()).isNaN();
    }

    @Test
    void stale() {
        // given
        final var latency = new Latency();

        // when
        latency.record(10);

        // then
        assertThat(latency.getAverage()).isCloseTo(10.0, OFFSET);

        // when, then only the first call changes the state
        assertThat(latency.markStale()).isTrue();
        assertThat(latency.markStale()).isFalse();

        // then
        assertThat(latency.getAverage()).isCloseTo(Double.MAX_VALUE, OFFSET);
        assertThat(latency.isFresh(Long.MAX_VALUE)).isFalse();

        // when
        latency.record(20);

        // then
        assertThat(latency.getAverage()).isCloseTo(13, OFFSET);
    }

    @Test
    void uninitialized() {
        // given
        final var latency = new Latency();

        // when, then
        assertThat(latency.getAverage()).isCloseTo(Double.MAX_VALUE, OFFSET);
        assertThat(latency.isFresh(Long.MAX_VALUE)).isFalse();
    }
}
