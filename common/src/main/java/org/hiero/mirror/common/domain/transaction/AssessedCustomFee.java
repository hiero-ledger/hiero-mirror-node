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
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@NoArgsConstructor
@Table
public class AssessedCustomFee implements Persistable<AssessedCustomFee.Id> {

    private long amount;

    @Transient
    private long collectorAccountId;

    @Transient
    private long consensusTimestamp;

    @Builder.Default
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> effectivePayerAccountIds = Collections.emptyList();

    private EntityId tokenId;

    private EntityId payerAccountId;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    @Override
    public Id getId() {
        var id = new Id();
        id.setCollectorAccountId(collectorAccountId);
        id.setConsensusTimestamp(consensusTimestamp);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid querying before insert
    }

    public void setId(Id id) {
        if (id == null) {
            return;
        }

        this.collectorAccountId = id.getCollectorAccountId();
        this.consensusTimestamp = id.getConsensusTimestamp();
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
