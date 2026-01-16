// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.topic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder(toBuilder = true)
@Data
@Entity
@IdClass(TopicMessageLookup.Id.class)
@NoArgsConstructor
@Upsertable
public class TopicMessageLookup {

    private static final String COALESCE_RANGE = "int8range(coalesce(lower(e_{0}), lower({0})), upper({0}))";

    @jakarta.persistence.Id
    private String partition;

    @UpsertColumn(coalesce = COALESCE_RANGE)
    private Range<Long> sequenceNumberRange;

    @UpsertColumn(coalesce = COALESCE_RANGE)
    private Range<Long> timestampRange;

    @jakarta.persistence.Id
    private long topicId;

    public static TopicMessageLookup from(String partition, TopicMessage topicMessage) {
        long sequenceNumber = topicMessage.getSequenceNumber();
        long timestamp = topicMessage.getConsensusTimestamp();
        return TopicMessageLookup.builder()
                .partition(partition)
                .sequenceNumberRange(Range.closedOpen(sequenceNumber, sequenceNumber + 1))
                .timestampRange(Range.closedOpen(timestamp, timestamp + 1))
                .topicId(topicMessage.getTopicId().getId())
                .build();
    }

    @JsonIgnore
    public Id getId() {
        var id = new Id();
        id.setPartition(partition);
        id.setTopicId(topicId);
        return id;
    }

    @Data
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = 5704900912468270592L;

        private String partition;
        private long topicId;
    }
}
