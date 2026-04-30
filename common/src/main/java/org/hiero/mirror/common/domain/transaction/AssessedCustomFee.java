// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("assessed_custom_fee")
@NoArgsConstructor
public class AssessedCustomFee implements Persistable<AssessedCustomFee.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private long amount;

    @Builder.Default
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> effectivePayerAccountIds = Collections.emptyList();

    private EntityId tokenId;

    private EntityId payerAccountId;

    /**
     * Custom builder to maintain compatibility with existing code that sets
     * collectorAccountId and consensusTimestamp directly.
     */
    public static class AssessedCustomFeeBuilder {
        public AssessedCustomFeeBuilder collectorAccountId(long collectorAccountId) {
            initId();
            this.id.setCollectorAccountId(collectorAccountId);
            return this;
        }

        public AssessedCustomFeeBuilder consensusTimestamp(long consensusTimestamp) {
            initId();
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        private void initId() {
            if (this.id == null) {
                this.id = new Id();
            }
        }
    }

    // Convenience accessors
    public long getCollectorAccountId() {
        return id != null ? id.getCollectorAccountId() : 0L;
    }

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    @JsonIgnore
    @Override
    public Id getId() {
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = -636368167561206418L;

        private long collectorAccountId;
        private long consensusTimestamp;
    }
}
