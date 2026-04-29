// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.addressbook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("node_stake") // Explicit naming is best practice
@NoArgsConstructor
public class NodeStake implements Persistable<NodeStake.Id> {

    @org.springframework.data.annotation.Id
    private long consensusTimestamp;

    private long epochDay;

    private long maxStake;

    private long minStake;

    @org.springframework.data.annotation.Id
    private long nodeId;

    private long rewardRate;

    private long stake;

    private long stakeNotRewarded;

    private long stakeRewarded;

    private long stakingPeriod;

    @JsonIgnore
    @Override
    public Id getId() {
        return new Id(consensusTimestamp, nodeId);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        // Keeps the optimization to skip the "SELECT" check before insert
        return true;
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -2513526593205520365L;

        private long consensusTimestamp;
        private long nodeId;
    }
}
