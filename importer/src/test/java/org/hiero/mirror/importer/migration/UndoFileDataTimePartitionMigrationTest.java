// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.flywaydb.core.Flyway;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV2;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@DisableRepeatableSqlMigration
@EnabledIfV2
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=2.1.0")
class UndoFileDataTimePartitionMigrationTest extends ImporterIntegrationTest {

    private static final String REVERT_DDL_TEMPLATE = """
            drop table if exists file_data;
            create table if not exists file_data
            (
                consensus_timestamp bigint   not null,
                entity_id           bigint   not null,
                file_data           bytea    null,
                transaction_type    smallint not null
            ) partition by range (consensus_timestamp);
            select create_distributed_table('file_data', 'entity_id', colocate_with => 'entity');
            select create_time_partitions(table_name := 'public.file_data',
                partition_interval := %1$s,
                start_from := %2$s::timestamptz,
                end_at := CURRENT_TIMESTAMP + %1$s);
            alter table if exists file_data
                add constraint file_data__pk primary key (consensus_timestamp, entity_id);
            create index if not exists file_data__id_timestamp
                on file_data (entity_id, consensus_timestamp);
            """;

    private final FileDataRepository fileDataRepository;
    private final Flyway flyway;

    @Value("classpath:db/migration/v2/V2.2.0__undo_file_data_time_partition.sql")
    private final Resource migrationSql;

    @AfterEach
    void cleanup() {
        var flywayPlaceHolder = flyway.getConfiguration().getPlaceholders();
        var partitionStartDate = flywayPlaceHolder.get("partitionStartDate");
        var partitionTimeInterval = flywayPlaceHolder.get("partitionTimeInterval");
        var revertDdl = String.format(REVERT_DDL_TEMPLATE, partitionTimeInterval, partitionStartDate);
        ownerJdbcTemplate.execute(revertDdl);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(fileDataRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        var fileData1 = domainBuilder.fileData().persist();
        var fileData2 = domainBuilder.fileData().persist();
        runMigration();
        assertThat(fileDataRepository.findAll()).containsExactlyInAnyOrder(fileData1, fileData2);
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = migrationSql.getInputStream()) {
            var script = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            ownerJdbcTemplate.execute(script);
        }
    }
}
