// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.parser.domain.RecordFileBuilder;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.test.performance.PerformanceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.EnabledIf;

@CustomLog
@EnabledIf(expression = "${hiero.mirror.importer.test.performance.parser.enabled}", loadContext = true)
@RequiredArgsConstructor
@Tag("performance")
class RecordFileParserPerformanceTest extends ImporterIntegrationTest {

    private final PerformanceProperties performanceProperties;
    private final RecordFileParser recordFileParser;
    private final RecordFileBuilder recordFileBuilder;
    private final RecordFileRepository recordFileRepository;

    @BeforeEach
    void setup() {
        recordFileParser.clear();
    }

    @Test
    void scenarios() {
        var properties = performanceProperties.getParser();
        var previous = recordFileRepository.findLatest().orElse(null);
        var scenarios = performanceProperties.getScenarios().getOrDefault(properties.getScenario(), List.of());

        for (var scenario : scenarios) {
            if (!scenario.isEnabled()) {
                log.info("Scenario {} is disabled", scenario.getDescription());
                continue;
            }

            log.info("Executing scenario: {}", scenario);
            long interval = StreamType.RECORD.getFileCloseInterval().toMillis();
            long duration = scenario.getDuration().toMillis();
            long startTime = System.currentTimeMillis();
            long endTime = startTime;
            var stats = new SummaryStatistics();
            var stopwatch = Stopwatch.createStarted();
            var builder = recordFileBuilder.recordFile();

            scenario.getTransactions().forEach(p -> {
                int count = (int) (p.getTps() * interval / 1000);
                builder.recordItems(i -> i.count(count)
                        .entities(p.getEntities())
                        .entityAutoCreation(true)
                        .subType(p.getSubType())
                        .type(p.getType()));
            });

            while (endTime - startTime < duration) {
                var recordFile = builder.previous(previous).build();
                long startNanos = System.nanoTime();
                recordFileParser.parse(recordFile);
                stats.addValue(System.nanoTime() - startNanos);
                previous = recordFile;

                long sleep = interval - (System.currentTimeMillis() - endTime);
                if (sleep > 0) {
                    Uninterruptibles.sleepUninterruptibly(sleep, TimeUnit.MILLISECONDS);
                }
                endTime = System.currentTimeMillis();
            }

            long mean = (long) (stats.getMean() / 1_000_000.0);
            log.info(
                    "Scenario {} took {} to process {} files for a mean of {} ms per file",
                    scenario.getDescription(),
                    stopwatch,
                    stats.getN(),
                    mean);
            assertThat(Duration.ofMillis(mean))
                    .as("Scenario {} had a latency of {} ms", scenario.getDescription(), mean)
                    .isLessThanOrEqualTo(properties.getLatency());
        }
    }
}
