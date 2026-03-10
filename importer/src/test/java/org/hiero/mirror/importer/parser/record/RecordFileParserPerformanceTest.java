// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.parser.domain.RecordFileBuilder;
import org.hiero.mirror.importer.reader.record.CompositeRecordFileReader;
import org.hiero.mirror.importer.reader.record.ProtoRecordFileReader;
import org.hiero.mirror.importer.reader.record.RecordFileReaderImplV1;
import org.hiero.mirror.importer.reader.record.RecordFileReaderImplV2;
import org.hiero.mirror.importer.reader.record.RecordFileReaderImplV5;
import org.hiero.mirror.importer.repository.EntityTransactionRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
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
    private final TransactionRepository transactionRepository;
    private final EntityTransactionRepository entityTransactionRepository;

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

    @Test
    void parseRecordFilesFromDirectory() throws Exception {
        var properties = performanceProperties.getParser();
        var directoryPath = Path.of("/Users/bilyanagospodinova/RecordFilesSmall1");
        if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
            throw new IllegalArgumentException(
                    "Record file directory does not exist or is not a directory: " + directoryPath);
        }

        var recordFileReader = new CompositeRecordFileReader(
                new RecordFileReaderImplV1(),
                new RecordFileReaderImplV2(),
                new RecordFileReaderImplV5(),
                new ProtoRecordFileReader());

        List<Path> rcdFiles;
        try (var paths = Files.walk(directoryPath)) {
            rcdFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".rcd.gz"))
                    .sorted()
                    .toList();
        }

        assertThat(rcdFiles).as("No *.rcd.gz files in %s", directoryPath).isNotEmpty();

        var stats = new SummaryStatistics();
        var stopwatch = Stopwatch.createStarted();

        for (Path filePath : rcdFiles) {
            File file = filePath.toFile();
            if (!file.exists()) {
                throw new IllegalArgumentException("File does not exist: " + filePath);
            }
            if (!file.getName().endsWith(".rcd.gz")) {
                throw new IllegalArgumentException("File must be a *.rcd.gz file: " + filePath);
            }
            log.info("Processing file: {}", file.getAbsolutePath());
            StreamFileData streamFileData = StreamFileData.from(file);
            RecordFile recordFile = recordFileReader.read(streamFileData);

            long startNanos = System.nanoTime();
            recordFileParser.parse(recordFile);
            stats.addValue(System.nanoTime() - startNanos);
        }

        long meanMs = (long) (stats.getMean() / 1_000_000.0);
        log.info(
                "Parsed {} record files from directory {} in {} for a mean of {} ms per file",
                stats.getN(),
                directoryPath,
                stopwatch,
                meanMs);
        assertThat(Duration.ofMillis(meanMs))
                .as("Directory parse had a mean latency of %d ms", meanMs)
                .isLessThanOrEqualTo(properties.getLatency());
        log.info("Inserted {} rows into transaction table.", transactionRepository.count());
        log.info("Inserted {} rows into entity_transaction table.", entityTransactionRepository.count());
    }
}
