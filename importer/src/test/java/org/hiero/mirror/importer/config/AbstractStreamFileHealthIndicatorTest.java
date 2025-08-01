// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.downloader.Downloader.STREAM_CLOSE_LATENCY_METRIC_NAME;
import static org.hiero.mirror.importer.parser.AbstractStreamFileParser.STREAM_PARSE_DURATION_METRIC_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doReturn;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.parser.AbstractParserProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
abstract class AbstractStreamFileHealthIndicatorTest {

    private static final String REASON_KEY = "reason";

    @Mock(strictness = LENIENT)
    private Timer streamFileParseDurationTimer;

    @Mock(strictness = LENIENT)
    private Timer streamCloseLatencyDurationTimer;

    @Mock(strictness = LENIENT)
    private Search streamParseDurationSearch;

    @Mock(strictness = LENIENT)
    private Search streamCloseLatencySearch;

    @Mock(strictness = LENIENT)
    private MeterRegistry meterRegistry;

    private StreamFileHealthIndicator streamFileHealthIndicator;

    private AbstractParserProperties parserProperties;

    protected ImporterProperties importerProperties;

    abstract AbstractParserProperties getParserProperties();

    @BeforeEach
    void setup() {
        doReturn(0.0).when(streamCloseLatencyDurationTimer).mean(any());
        doReturn(0L).when(streamFileParseDurationTimer).count();

        doReturn(streamCloseLatencyDurationTimer).when(streamCloseLatencySearch).timer();
        doReturn(streamFileParseDurationTimer).when(streamParseDurationSearch).timer();

        doReturn(streamCloseLatencySearch).when(streamCloseLatencySearch).tags(anyIterable());
        doReturn(streamParseDurationSearch).when(streamParseDurationSearch).tags(anyIterable());

        doReturn(streamCloseLatencySearch).when(meterRegistry).find(STREAM_CLOSE_LATENCY_METRIC_NAME);
        doReturn(streamParseDurationSearch).when(meterRegistry).find(STREAM_PARSE_DURATION_METRIC_NAME);

        importerProperties = new ImporterProperties();
        importerProperties.setEndDate(Instant.MAX);
        parserProperties = getParserProperties();

        streamFileHealthIndicator = new StreamFileHealthIndicator(meterRegistry, importerProperties, parserProperties);
    }

    @Test
    void startupParsingDisabled() {
        parserProperties.setEnabled(false);

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY)).contains("Parsing is disabled");
    }

    @Test
    void missingParserDurationTimer() {
        doReturn(null).when(streamParseDurationSearch).timer();

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains(STREAM_PARSE_DURATION_METRIC_NAME + " timer is missing");
    }

    @Test
    void missingStreamCloseLatencyTimer() {
        doReturn(null).when(streamCloseLatencySearch).timer();

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains(STREAM_CLOSE_LATENCY_METRIC_NAME + " timer is missing");
    }

    @Test
    void startupNoStreamFilesBeforeWindow() {
        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY)).contains("Starting up, no files parsed yet");
        assertThat((Long) health.getDetails().get("count")).isZero();
    }

    @Test
    void startupNoStreamFilesAfterWindowBeforeFirstFileClose() {
        // set window time to smaller value
        parserProperties.setProcessingTimeout(Duration.ofSeconds(-10L));
        importerProperties.setStartDate(Instant.now().plus(1, ChronoUnit.DAYS));

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY)).contains("Starting up, no files parsed yet");
        assertThat((Long) health.getDetails().get("count")).isZero();
    }

    @Test
    void startupNoNewDownloadedStreamFilesAfterWindowBeforeFileClose() {
        // file close mean metric will be zero and default fileCLose duration will be used
        // set window time to smaller value
        parserProperties.setProcessingTimeout(Duration.ofMillis(1L));

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat((String) health.getDetails().get(REASON_KEY)).contains("Starting up, no files parsed yet");
        assertThat((Long) health.getDetails().get("count")).isZero();
    }

    @Test
    void startupNoStreamFilesAfterFirstFileClose() {
        // set window time to smaller value
        parserProperties.setProcessingTimeout(Duration.ofSeconds(-10L));
        importerProperties.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));

        // update fileClose mean otherwise larger default value is used
        doReturn(1.0).when(streamCloseLatencyDurationTimer).mean(any());

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains("No new stream stream files have been parsed since:");
        assertThat((Long) health.getDetails().get("count")).isZero();
    }

    @Test
    void newStreamFiles() {
        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();
        doReturn((double)
                        parserProperties.getStreamType().getFileCloseInterval().toMillis())
                .when(streamFileParseDurationTimer)
                .mean(any());

        health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void noNewStreamFilesInWindow() {
        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();
        doReturn((double)
                        parserProperties.getStreamType().getFileCloseInterval().toMillis())
                .when(streamCloseLatencyDurationTimer)
                .mean(any());

        streamFileHealthIndicator.health();

        health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health.getStatus()).isEqualTo(Status.UP); // cache should be returned
    }

    @Test
    void noNewStreamFilesAfterWindow() {
        //        parserProperties.setProcessingTimeout(Duration.ofMinutes(-10L)); // force end of timeout to before now
        importerProperties.setStartDate(Instant.EPOCH);

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);

        // update fileClose mean otherwise larger default value is used
        doReturn(1.0).when(streamCloseLatencyDurationTimer).mean(any());
        parserProperties.setProcessingTimeout(Duration.ofMinutes(-10L)); // force end of timeout to before now

        health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health.getStatus()).isEqualTo(Status.DOWN); // cache should not be returned
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains("No new stream stream files have been parsed since:");
        assertThat((Long) health.getDetails().get("count")).isEqualTo(1);
    }

    @Test
    void noNewStreamFilesAfterWindowAndEndTime() {
        importerProperties.setEndDate(Instant.now().minusSeconds(60)); // force endDate to before now
        parserProperties = getParserProperties();

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP); // cache should not be returned
        assertThat((String) health.getDetails().get(REASON_KEY)).contains("stream files are no longer expected");
    }

    @Test
    void recoverWhenNewStreamFiles() {
        importerProperties.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));

        Health health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);

        // update counter
        doReturn(1L).when(streamFileParseDurationTimer).count();

        health = streamFileHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);

        // update fileClose mean otherwise larger default value is used
        doReturn(1.0).when(streamCloseLatencyDurationTimer).mean(any());
        parserProperties.setProcessingTimeout(Duration.ofSeconds(-10L)); // force end of timeout to before now

        health = streamFileHealthIndicator.health(); // count unchanged
        assertThat(health.getStatus()).isEqualTo(Status.DOWN); // cache should not be returned
        assertThat((String) health.getDetails().get(REASON_KEY))
                .contains("No new stream stream files have been parsed since:");
        assertThat((Long) health.getDetails().get("count")).isEqualTo(1);

        doReturn(2L).when(streamFileParseDurationTimer).count();

        health = streamFileHealthIndicator.health(); // count incremented
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }
}
