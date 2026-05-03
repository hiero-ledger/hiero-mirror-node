// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;

public interface History {

    @JsonIgnore
    default boolean hasHistory() {
        return getTimestampRange() != null;
    }

    Range<Long> getTimestampRange();

    void setTimestampRange(Range<Long> timestampRange);

    @JsonIgnore
    default Long getTimestampLower() {
        var timestampRange = getTimestampRange();
        return timestampRange != null && timestampRange.hasLowerBound() ? timestampRange.lowerEndpoint() : null;
    }

    default void setTimestampLower(long timestampLower) {
        setTimestampRange(Range.atLeast(timestampLower));
    }

    @JsonIgnore
    default Long getTimestampUpper() {
        var timestampRange = getTimestampRange();
        return timestampRange != null && timestampRange.hasUpperBound() ? timestampRange.upperEndpoint() : null;
    }

    default void setTimestampUpper(long timestampUpper) {
        var timestampLower = getTimestampLower();
        if (timestampLower != null) {
            setTimestampRange(Range.closedOpen(timestampLower, timestampUpper));
        }
    }
}
