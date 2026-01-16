// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import java.util.List;

public interface TimePartitionService {
    /**
     * Get the time partitions overlapping the range [fromTimestamp, toTimestamp]
     *
     * @param tableName The table name
     * @param fromTimestamp The from timestamp, inclusive
     * @param toTimestamp The to timestamp, inclusive
     * @return The overlapping time partitions
     */
    List<TimePartition> getOverlappingTimePartitions(String tableName, long fromTimestamp, long toTimestamp);

    /**
     * Get the time partitions for a given table. The returned time partitions are sorted by the timestamp range
     * in ascending order.
     *
     * @param tableName The table name
     * @return The time partitions. If the table is not time partitioned or doesn't have time partitions, returns an empty list
     */
    List<TimePartition> getTimePartitions(String tableName);
}
