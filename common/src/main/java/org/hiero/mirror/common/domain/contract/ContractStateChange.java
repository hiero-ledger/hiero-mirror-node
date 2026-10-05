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

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder(toBuilder = true)
@Data
@Table
@NoArgsConstructor
public class ContractStateChange implements Persistable<ContractStateChange.Id> {

    @JsonIgnore
    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private boolean migration;

    private EntityId payerAccountId;

    @ToString.Exclude
    private byte[] valueRead;

    @ToString.Exclude
    private byte[] valueWritten;

    public void setConsensusTimestamp(long consensusTimestamp) {
        id().setConsensusTimestamp(consensusTimestamp);
    }

    public void setContractId(EntityId entityId) {
        id().setContractId(entityId.getId());
    }

    public void setContractId(long contractId) {
        id().setContractId(contractId);
    }

    public void setSlot(byte[] slot) {
        id().setSlot(slot);
    }

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public long getContractId() {
        return id != null ? id.getContractId() : 0L;
    }

    public byte[] getSlot() {
        return id != null ? id.getSlot() : null;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    private Id id() {
        if (id == null) {
            id = new Id();
        }
        return id;
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -3677350664183037811L;

        private long consensusTimestamp;

        private long contractId;

        private byte[] slot;
    }

    public static class ContractStateChangeBuilder {

        private Id ensureId() {
            this.id = this.id == null
                    ? new Id()
                    : new Id(this.id.getConsensusTimestamp(), this.id.getContractId(), this.id.getSlot());
            return this.id;
        }

        public ContractStateChangeBuilder consensusTimestamp(long consensusTimestamp) {
            ensureId().setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public ContractStateChangeBuilder contractId(long contractId) {
            ensureId().setContractId(contractId);
            return this;
        }

        public ContractStateChangeBuilder slot(byte[] slot) {
            ensureId().setSlot(slot);
            return this;
        }
    }
}
