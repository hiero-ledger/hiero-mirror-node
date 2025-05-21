// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Entity
@IdClass(StakingRewardTransfer.Id.class)
@NoArgsConstructor
public class StakingRewardTransfer implements Persistable<StakingRewardTransfer.Id> {

    @jakarta.persistence.Id
    private long accountId;

    private long amount;

    @jakarta.persistence.Id
    private long consensusTimestamp;

    private EntityId payerAccountId;

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
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    public static class Id implements Serializable {
        private static final long serialVersionUID = 1129458229846263861L;

        private long accountId;

        private long consensusTimestamp;
    }
}
