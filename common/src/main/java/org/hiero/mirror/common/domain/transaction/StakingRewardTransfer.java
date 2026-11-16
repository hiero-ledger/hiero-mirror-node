// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
public class StakingRewardTransfer implements Persistable<StakingRewardTransfer.Id> {

    @Transient
    private long accountId;

    private long amount;

    @Transient
    private long consensusTimestamp;

    private EntityId payerAccountId;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    @Override
    public Id getId() {
        Id id = new Id();
        id.setAccountId(accountId);
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

        this.accountId = id.getAccountId();
        this.consensusTimestamp = id.getConsensusTimestamp();
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = 1129458229846263861L;

        private long accountId;

        private long consensusTimestamp;
    }
}
