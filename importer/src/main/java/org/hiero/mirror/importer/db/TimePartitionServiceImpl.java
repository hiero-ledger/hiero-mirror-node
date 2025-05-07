// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_TIME_PARTITION;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_TIME_PARTITION_OVERLAP;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@CustomLog
@Named
public class TimePartitionServiceImpl implements TimePartitionService {

    private static final String GET_TIME_PARTITIONS_SQL = "select * from mirror_node_time_partitions where parent = ?";
    private static final RowMapper<TimePartition> ROW_MAPPER = (rs, rowNum) -> TimePartition.builder()
            .name(rs.getString("name"))
            .parent(rs.getString("parent"))
            .timestampRange(Range.closedOpen(rs.getLong("from_timestamp"), rs.getLong("to_timestamp")))
            .build();

    private final Cache cacheTimePartitionOverlap;
    private final Cache cacheTimePartition;
    private final JdbcTemplate jdbcTemplate;

    TimePartitionServiceImpl(
            @Qualifier(CACHE_TIME_PARTITION_OVERLAP) CacheManager cacheManagerOverlapTimePartition,
            @Qualifier(CACHE_TIME_PARTITION) CacheManager cacheManagerTimePartition,
            JdbcTemplate jdbcTemplate) {
        this.cacheTimePartitionOverlap = cacheManagerOverlapTimePartition.getCache(CACHE_NAME);
        this.cacheTimePartition = cacheManagerTimePartition.getCache(CACHE_NAME);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TimePartition> getOverlappingTimePartitions(String tableName, long fromTimestamp, long toTimestamp) {
        String cacheKey = tableName + "-" + fromTimestamp + "-" + toTimestamp;
        return cacheTimePartitionOverlap.get(
                cacheKey, () -> queryForOverlappingTimePartitions(tableName, fromTimestamp, toTimestamp));
    }

    @Override
    public List<TimePartition> getTimePartitions(String tableName) {
        return cacheTimePartition.get(tableName, () -> queryForTimePartitions(tableName));
    }

    private List<TimePartition> queryForOverlappingTimePartitions(
            String tableName, long fromTimestamp, long toTimestamp) {
        if (toTimestamp < fromTimestamp) {
            return Collections.emptyList();
        }

        var partitions = getTimePartitions(tableName);
        if (partitions.isEmpty()) {
            return Collections.emptyList();
        }

        int index = Collections.binarySearch(partitions, null, (current, key) -> {
            if (current.getTimestampRange().contains(fromTimestamp)) {
                return 0;
            }

            return current.getTimestampRange().lowerEndpoint() < fromTimestamp ? -1 : 1;
        });

        var overlappingPartitions = new ArrayList<TimePartition>();
        if (index >= 0) {
            overlappingPartitions.add(partitions.get(index));
            index++;
        } else {
            int insertIndex = -1 - index;
            if (insertIndex == partitions.size()) {
                // all partitions are before fromTimestamp
                return Collections.emptyList();
            }

            // otherwise fromTimestamp is before the first partition
            index = 0;
        }

        for (; index < partitions.size(); index++) {
            var partition = partitions.get(index);
            if (toTimestamp >= partition.getTimestampRange().lowerEndpoint()) {
                overlappingPartitions.add(partition);
            } else {
                break;
            }
        }

        return Collections.unmodifiableList(overlappingPartitions);
    }

    private List<TimePartition> queryForTimePartitions(String tableName) {
        try {
            var partitions = jdbcTemplate.query(GET_TIME_PARTITIONS_SQL, ROW_MAPPER, tableName);
            if (partitions.isEmpty()) {
                return Collections.emptyList();
            }

            return Collections.unmodifiableList(partitions);
        } catch (Exception e) {
            log.warn("Unable to query time partitions for table {}", tableName, e);
            return Collections.emptyList();
        }
    }
}
