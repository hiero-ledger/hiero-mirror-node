// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Table("contract_action")
@NoArgsConstructor
public class ContractAction implements Persistable<ContractAction.Id> {

    private int callDepth;

    // Handled by global EntityId Reading/Writing converters
    private EntityId caller;

    // Handled by global EntityType converter (if custom PG type)
    private EntityType callerType;

    private int callOperationType;

    private Integer callType;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private long gas;

    private long gasUsed;

    @ToString.Exclude
    private byte[] input;

    private EntityId payerAccountId;

    private EntityId recipientAccount;

    @ToString.Exclude
    private byte[] recipientAddress;

    private EntityId recipientContract;

    @ToString.Exclude
    private byte[] resultData;

    private int resultDataType;

    private long value;

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public void setConsensusTimestamp(long consensusTimestamp) {
        if (id == null) {
            id = new Id();
        }
        id.setConsensusTimestamp(consensusTimestamp);
    }

    public int getIndex() {
        return id != null ? id.getIndex() : 0;
    }

    public void setIndex(int index) {
        if (id == null) {
            id = new Id();
        }
        id.setIndex(index);
    }

    @Override
    @JsonIgnore
    public ContractAction.Id getId() {
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    @JsonIgnore
    public boolean hasRevertReason() {
        return resultDataType == REVERT_REASON.getNumber();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = -6192177810161178246L;
        private long consensusTimestamp;
        private int index;
    }

    public static class ContractActionBuilder {
        public ContractActionBuilder consensusTimestamp(long consensusTimestamp) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public ContractActionBuilder index(int index) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setIndex(index);
            return this;
        }
    }
}
