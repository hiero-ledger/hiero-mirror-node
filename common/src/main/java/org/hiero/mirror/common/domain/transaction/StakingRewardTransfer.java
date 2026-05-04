// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("staking_reward_transfer")
@NoArgsConstructor
public class StakingRewardTransfer implements Persistable<StakingRewardTransfer.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private long amount;

    private EntityId payerAccountId;

    public static class StakingRewardTransferBuilder {
        public StakingRewardTransferBuilder accountId(long accountId) {
            initId();
            this.id.setAccountId(accountId);
            return this;
        }

        public StakingRewardTransferBuilder consensusTimestamp(long consensusTimestamp) {
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

    public long getAccountId() {
        return id != null ? id.getAccountId() : 0L;
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
        private static final long serialVersionUID = 1129458229846263861L;

        private long accountId;
        private long consensusTimestamp;
    }
}
