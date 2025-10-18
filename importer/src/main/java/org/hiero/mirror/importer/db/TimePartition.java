// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import com.google.common.collect.Range;
import java.util.Comparator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder
@Data
public class TimePartition implements Comparable<TimePartition> {
    private static final Comparator<TimePartition> COMPARATOR = Comparator.nullsFirst(
            Comparator.comparingLong(t -> t.getTimestampRange().lowerEndpoint()));

    private String name;
    private String parent;
    private Range<Long> timestampRange;

    @Override
    public int compareTo(@Nullable TimePartition other) {
        return COMPARATOR.compare(this, other);
    }

    public long getEnd() {
        return timestampRange.upperEndpoint() - 1;
    }
}
