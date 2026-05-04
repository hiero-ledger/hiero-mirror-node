// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("contract_state_change")
@NoArgsConstructor
public class ContractStateChange implements Persistable<ContractStateChange.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private boolean migration;

    private EntityId payerAccountId;

    @ToString.Exclude
    private byte[] valueRead;

    @ToString.Exclude
    private byte[] valueWritten;

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public void setConsensusTimestamp(long consensusTimestamp) {
        if (id == null) {
            id = new Id();
        }
        id.setConsensusTimestamp(consensusTimestamp);
    }

    public long getContractId() {
        return id != null ? id.getContractId() : 0L;
    }

    public void setContractId(EntityId entityId) {
        if (id == null) {
            id = new Id();
        }
        id.setContractId(entityId.getId());
    }

    public void setContractId(long contractId) {
        if (id == null) {
            id = new Id();
        }
        id.setContractId(contractId);
    }

    public byte[] getSlot() {
        return id != null ? id.getSlot() : null;
    }

    public void setSlot(byte[] slot) {
        if (id == null) {
            id = new Id();
        }
        id.setSlot(slot);
    }

    @Override
    @JsonIgnore
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
        private static final long serialVersionUID = -3677350664183037811L;

        private long consensusTimestamp;
        private long contractId;

        @ToString.Exclude
        private byte[] slot;
    }

    public static class ContractStateChangeBuilder {
        public ContractStateChangeBuilder consensusTimestamp(long consensusTimestamp) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public ContractStateChangeBuilder contractId(long contractId) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setContractId(contractId);
            return this;
        }

        public ContractStateChangeBuilder slot(byte[] slot) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setSlot(slot);
            return this;
        }
    }
}
