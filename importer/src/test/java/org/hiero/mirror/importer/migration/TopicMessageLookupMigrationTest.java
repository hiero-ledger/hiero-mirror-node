// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.importer.TestUtils.plus;

import com.google.common.collect.Range;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.callback.SimpleContext;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.TopicMessageLookup;
import org.hiero.mirror.importer.EnabledIfV2;
import org.hiero.mirror.importer.parser.record.entity.topic.AbstractTopicMessageLookupIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@EnabledIfV2
@RequiredArgsConstructor
@Tag("migration")
@ExtendWith({OutputCaptureExtension.class})
class TopicMessageLookupMigrationTest extends AbstractTopicMessageLookupIntegrationTest {

    private static final String PARTITION_SKIPPED_TEMPLATE = "Partition %s doesn't need migration";
    private static final String RESET_CHECKSUM_SQL =
            "update flyway_schema_history set checksum = -1 where description = ?";
    private static final String SELECT_LAST_CHECKSUM_SQL = """
            select (
              select checksum from flyway_schema_history
              where description = ?
              order by installed_rank desc
              limit 1
            )
            """;

    private final TopicMessageLookupMigration migration;

    @AfterEach
    @BeforeEach
    void resetChecksum() {
        jdbcOperations.update(RESET_CHECKSUM_SQL, migration.getDescription());
    }

    @Test
    void disabled() {
        // given
        entityProperties.getPersist().setTopicMessageLookups(false);
        long partition1Start = partitions.get(0).getTimestampRange().lowerEndpoint();
        domainBuilder
                .recordFile()
                .customize(r ->
                        r.consensusStart(partition1Start).consensusEnd(plus(partition1Start, RECORD_FILE_INTERVAL)))
                .persist();
        domainBuilder
                .topicMessage()
                .customize(t -> t.consensusTimestamp(partition1Start))
                .persist();

        // when
        runMigration();

        // then
        assertThat(topicMessageLookupRepository.count()).isZero();
    }

    @Test
    void empty() {
        runMigration();
        assertThat(topicMessageLookupRepository.count()).isZero();
    }

    @Test
    void emptyTopicMessage() {
        // given
        long consensusStart = partitions.get(0).getTimestampRange().lowerEndpoint();
        domainBuilder
                .recordFile()
                .customize(
                        r -> r.consensusStart(consensusStart).consensusEnd(plus(consensusStart, RECORD_FILE_INTERVAL)))
                .persist();

        // when
        runMigration();

        // then
        assertThat(topicMessageLookupRepository.count()).isZero();
    }

    @Test
    void idempotent(CapturedOutput output) {
        var activeMessage = "Migrating topic_message_lookup for partition "
                + partitions.get(1).getName() + " synchronously";
        var expectedPartitions = Arrays.asList(partitions.get(0), partitions.get(1));

        migrate(true);
        assertThat(output).contains(activeMessage);
        for (var partition : expectedPartitions) {
            assertThat(output).doesNotContain(String.format(PARTITION_SKIPPED_TEMPLATE, partition.getName()));
        }

        jdbcOperations.update(RESET_CHECKSUM_SQL, migration.getClass().getName());

        migrate(false);
        for (var partition : expectedPartitions) {
            assertThat(output).contains(String.format(PARTITION_SKIPPED_TEMPLATE, partition.getName()));
        }
    }

    @Test
    void migrateOnce() {
        migrate(true);
    }

    @Test
    void onlyUnprocessedPartitionsAreProcessed(CapturedOutput output) {
        migrate(true);
        var closedPartition = partitions.get(0);
        var openPartition = partitions.get(1);
        var deleteSql = "delete from topic_message_lookup where partition = ?";
        ownerJdbcTemplate.update(deleteSql, closedPartition.getName());
        jdbcOperations.update(RESET_CHECKSUM_SQL, migration.getClass().getName());

        migrate(false);
        assertThat(output)
                .contains(String.format(PARTITION_SKIPPED_TEMPLATE, openPartition.getName()))
                .doesNotContain(String.format(PARTITION_SKIPPED_TEMPLATE, closedPartition.getName()));
    }

    @Test
    void onlyProcessPartitionsPriorToRecordFile() {
        var beforeRecordFilePartition = partitions.getFirst().getTimestampRange();
        var recordFileRange = Optional.of(
                Range.closed(beforeRecordFilePartition.lowerEndpoint(), beforeRecordFilePartition.upperEndpoint() - 1));

        migrate(true, false, recordFileRange);
    }

    @Test
    void recordFileSpansPartitionOnlyMigratesLastPartition(CapturedOutput output) {
        var beforeRecordFilePartition = partitions.getFirst().getTimestampRange();
        var recrodFileRange = Optional.of(
                Range.closed(beforeRecordFilePartition.lowerEndpoint(), beforeRecordFilePartition.upperEndpoint()));

        migrate(true, true, recrodFileRange);

        assertThat(output)
                .contains("Migrating topic_message_lookup for partition "
                        + partitions.get(1).getName() + " synchronously")
                .doesNotContain("Migrating topic_message_lookup for partition "
                        + partitions.get(0).getName() + " synchronously");
    }

    private void migrate(boolean loadData) {
        migrate(loadData, true, Optional.empty());
    }

    private void migrate(boolean loadData, boolean expectPartition2, Optional<Range<Long>> recordFileBound) {
        // given
        // the first partition is closed
        var partition1 = partitions.get(0);
        var partition2 = partitions.get(1);
        long partition1Start = partition1.getTimestampRange().lowerEndpoint();
        long partition2Start = partition2.getTimestampRange().lowerEndpoint();
        long consensusStart = recordFileBound
                .map(Range::lowerEndpoint)
                .orElse(plus(partition2.getTimestampRange().lowerEndpoint(), Duration.ofSeconds(10)));
        long consensusEnd =
                recordFileBound.map(Range::upperEndpoint).orElse(plus(consensusStart, RECORD_FILE_INTERVAL));
        var topicId1 = EntityId.of(5000);
        var topicId2 = EntityId.of(5001);
        var topicId3 = EntityId.of(5002);

        if (loadData) {
            domainBuilder
                    .recordFile()
                    .customize(r -> r.consensusStart(consensusStart).consensusEnd(consensusEnd))
                    .persist();

            List.of(topicId1, topicId2, topicId3).forEach(topicId -> domainBuilder
                    .topicEntity()
                    .customize(t -> t.id(topicId.getId()).num(topicId.getNum()))
                    .persist());

            // partition 1
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(partition1Start)
                            .sequenceNumber(1)
                            .topicId(topicId1))
                    .persist();
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(partition1Start + 1)
                            .sequenceNumber(1)
                            .topicId(topicId2))
                    .persist();
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(partition1Start + 2)
                            .sequenceNumber(2)
                            .topicId(topicId2))
                    .persist();

            // partition 2
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(partition2Start)
                            .sequenceNumber(2)
                            .topicId(topicId1))
                    .persist();
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(partition2Start + 1)
                            .sequenceNumber(3)
                            .topicId(topicId2))
                    .persist();
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(partition2Start + 2)
                            .sequenceNumber(1)
                            .topicId(topicId3))
                    .persist();
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(partition2Start + 3)
                            .sequenceNumber(2)
                            .topicId(topicId3))
                    .persist();
        }

        // when
        runMigration();

        // then
        var expected = new ArrayList<>(List.of(
                TopicMessageLookup.builder()
                        .partition(partition1.getName())
                        .sequenceNumberRange(Range.closedOpen(1L, 2L))
                        .timestampRange(Range.closedOpen(partition1Start, partition1Start + 1))
                        .topicId(topicId1.getId())
                        .build(),
                TopicMessageLookup.builder()
                        .partition(partition1.getName())
                        .sequenceNumberRange(Range.closedOpen(1L, 3L))
                        .timestampRange(Range.closedOpen(partition1Start + 1, partition1Start + 3))
                        .topicId(topicId2.getId())
                        .build()));

        if (expectPartition2) {
            expected.addAll(List.of(
                    TopicMessageLookup.builder()
                            .partition(partition2.getName())
                            .sequenceNumberRange(Range.closedOpen(2L, 3L))
                            .timestampRange(Range.closedOpen(partition2Start, partition2Start + 1))
                            .topicId(topicId1.getId())
                            .build(),
                    TopicMessageLookup.builder()
                            .partition(partition2.getName())
                            .sequenceNumberRange(Range.closedOpen(3L, 4L))
                            .timestampRange(Range.closedOpen(partition2Start + 1, partition2Start + 2))
                            .topicId(topicId2.getId())
                            .build(),
                    TopicMessageLookup.builder()
                            .partition(partition2.getName())
                            .sequenceNumberRange(Range.closedOpen(1L, 3L))
                            .timestampRange(Range.closedOpen(partition2Start + 2, partition2Start + 4))
                            .topicId(topicId3.getId())
                            .build()));
        }

        assertThat(topicMessageLookupRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(migration.getChecksum()).isOne();
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
        migration.handle(Event.AFTER_MIGRATE_OPERATION_FINISH, new SimpleContext(new FluentConfiguration()));
        waitForCompletion();
    }

    private void waitForCompletion() {
        await().atMost(Duration.ofSeconds(30))
                .pollDelay(Duration.ofMillis(100))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(isMigrationCompleted()).isTrue());
    }

    private boolean isMigrationCompleted() {
        var actual = jdbcOperations.queryForObject(SELECT_LAST_CHECKSUM_SQL, Integer.class, migration.getDescription());
        return Objects.equals(actual, migration.getSuccessChecksum());
    }
}
