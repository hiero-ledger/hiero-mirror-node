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
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("node_stake")
@NoArgsConstructor
public class NodeStake implements Persistable<NodeStake.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private long epochDay;

    private long maxStake;

    private long minStake;

    private long rewardRate;

    private long stake;

    private long stakeNotRewarded;

    private long stakeRewarded;

    private long stakingPeriod;

    public void setConsensusTimestamp(long consensusTimestamp) {
        if (id == null) {
            id = new Id();
        }
        id.setConsensusTimestamp(consensusTimestamp);
    }

    public void setNodeId(long nodeId) {
        if (id == null) {
            id = new Id();
        }
        id.setNodeId(nodeId);
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
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
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

    public static class NodeStakeBuilder {
        public NodeStakeBuilder consensusTimestamp(long consensusTimestamp) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public NodeStakeBuilder nodeId(long nodeId) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setNodeId(nodeId);
            return this;
        }
    }
}
