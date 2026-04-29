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
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Table("staking_reward_transfer")
@NoArgsConstructor
public class StakingRewardTransfer implements Persistable<StakingRewardTransfer.Id> {

    @org.springframework.data.annotation.Id
    private long accountId;

    private long amount;

    @org.springframework.data.annotation.Id
    private long consensusTimestamp;

    // Handled by your global EntityId Reading/Writing converters
    private EntityId payerAccountId;

    @JsonIgnore
    @Override
    public Id getId() {
        return new Id(accountId, consensusTimestamp);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        // True ensures Spring Data JDBC executes an INSERT without checking if the record exists
        return true;
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
