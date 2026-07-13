// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.topic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("topic_message_lookup")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@Builder(toBuilder = true)
@Upsertable
public class TopicMessageLookup {

    private static final String COALESCE_RANGE = "int8range(coalesce(lower(e_{0}), lower({0})), upper({0}))";

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    @UpsertColumn(coalesce = COALESCE_RANGE)
    private Range<Long> sequenceNumberRange;

    @UpsertColumn(coalesce = COALESCE_RANGE)
    private Range<Long> timestampRange;

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

    public String getPartition() {
        return id != null ? id.getPartition() : null;
    }

    public void setPartition(String partition) {
        id = (id == null ? new Id() : id).withPartition(partition);
    }

    public long getTopicId() {
        return id != null ? id.getTopicId() : 0L;
    }

    public void setTopicId(long topicId) {
        id = (id == null ? new Id() : id).withTopicId(topicId);
    }

    @JsonIgnore
    public Id getId() {
        return id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @With
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = 5704900912468270592L;

        private String partition;
        private long topicId;
    }

    public static class TopicMessageLookupBuilder {
        public TopicMessageLookupBuilder partition(String partition) {
            this.id = (this.id == null ? new Id() : this.id).withPartition(partition);
            return this;
        }

        public TopicMessageLookupBuilder topicId(long topicId) {
            this.id = (this.id == null ? new Id() : this.id).withTopicId(topicId);
            return this;
        }
    }
}
