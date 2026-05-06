// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.EnabledIfV2;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.jdbc.core.DataClassRowMapper;

@RequiredArgsConstructor
@EnabledIfV2
class PartitionMaintenanceV2Test extends ImporterIntegrationTest {
    private static final String GET_LATEST_PARTITIONS = """
                    select distinct on (tp.parent_table) tp.parent_table,
                                   tp.partition_column,
                                   tp.partition,
                                   tp.from_value::bigint as current_from,
                                   tp.to_value::bigint as current_to,
                                   (greatest(0, extract(
                                     epoch from (to_timestamp(tp.from_value::bigint / 1000000000.0) - ?::interval))::bigint * 1000000000)
                                   ) as previous_from
                            from time_partitions tp
                            order by tp.parent_table, tp.from_value::bigint desc
                    """;
    private final PartitionMaintenance partitionMaintenance;
    private final FlywayProperties flywayProperties;

    private List<PartitionInfo> latestPartitions = new ArrayList<>();

    @BeforeEach
    void setup() {
        latestPartitions = getCurrentPartitions();
        assertThat(latestPartitions).isNotEmpty();
    }

    @Test
    void noPartitionsCreated() {
        partitionMaintenance.runMaintenance();
        assertThat(getCurrentPartitions()).isEqualTo(latestPartitions);
    }

    @Test
    void createsTimePartitions() {
        String dropLatestTimePartitions = latestPartitions.stream()
                .map(partitionInfo -> "drop table " + partitionInfo.partition)
                .collect(Collectors.joining(";\n"));
        ownerJdbcTemplate.execute(dropLatestTimePartitions);

        List<PartitionInfo> newLatestPartitions = getCurrentPartitions();

        assertThat(newLatestPartitions).hasSize(latestPartitions.size());

        // Verify partitions were removed
        for (var i = 0; i < latestPartitions.size(); i++) {
            var actual = newLatestPartitions.get(i);
            var expected = latestPartitions.get(i);
            assertThat(actual.currentTo).isEqualTo(expected.currentFrom);
            assertThat(actual.currentFrom).isEqualTo(expected.previousFrom);
        }
        partitionMaintenance.runMaintenance();

        // Verify partitions were re-created
        assertThat(getCurrentPartitions()).isEqualTo(latestPartitions);
    }

    private List<PartitionInfo> getCurrentPartitions() {
        return ownerJdbcTemplate.query(
                GET_LATEST_PARTITIONS,
                new DataClassRowMapper<>(PartitionInfo.class),
                flywayProperties.getPlaceholders().get("partitionTimeInterval"));
    }

    @Data
    private static class PartitionInfo {
        private String parentTable;
        private String partitionColumn;
        private String partition;
        private long currentFrom;
        private long previousFrom;
        private long currentTo;
    }
}
