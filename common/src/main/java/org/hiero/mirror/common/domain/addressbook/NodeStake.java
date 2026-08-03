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
import lombok.With;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder(toBuilder = true)
@Data
@Table
@NoArgsConstructor
public class NodeStake implements Persistable<NodeStake.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    /**
     * The epoch day of the ending staking period
     */
    private long epochDay;

    /**
     * The maximum stake (rewarded or not rewarded) this node can have as consensus weight. If its stake to reward is
     * above this maximum at the start of a period, then accounts staking to the node in that period will be rewarded at
     * a lower rate scaled by (maxStake / stakeRewardStart).
     */
    private long maxStake;

    /**
     * The minimum stake (rewarded or not rewarded) this node must reach before having non-zero consensus weight. If its
     * total stake is below this minimum at the start of a period, then accounts staking to the node in that period will
     * receive no rewards.
     */
    private long minStake;

    /**
     * The node's reward rate at the end of the staking period on epochDay
     */
    private long rewardRate;

    /**
     * The node consensus weight at the end of the staking period on epochDay
     */
    private long stake;

    /**
     * The sum of (balance + stakedToMe) for all accounts staked to this node with declineReward=true, at the end of the
     * staking period on epochDay
     */
    private long stakeNotRewarded;

    /**
     * The sum of (balance + stakedToMe) for all accounts staked to this node with declineReward=false, at the end of
     * the staking period on epochDay
     */
    private long stakeRewarded;

    /**
     * The timestamp of the end of the staking period on epochDay
     */
    private long stakingPeriod;

    public void setConsensusTimestamp(long consensusTimestamp) {
        id = (id == null ? new Id() : id).withConsensusTimestamp(consensusTimestamp);
    }

    public void setNodeId(long nodeId) {
        id = (id == null ? new Id() : id).withNodeId(nodeId);
    }

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public long getNodeId() {
        return id != null ? id.getNodeId() : 0L;
    }

    @JsonIgnore
    @Override
    public Id getId() {
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Spring Data JDBC querying before insert
    }

    @AllArgsConstructor
    @Builder
    @Data
    @NoArgsConstructor
    @With
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -2513526593205520365L;

        private long consensusTimestamp;
        private long nodeId;
    }
}
