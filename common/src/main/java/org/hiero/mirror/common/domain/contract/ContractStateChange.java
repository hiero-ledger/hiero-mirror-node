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
import lombok.With;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table
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

    // The id is replaced rather than mutated so instances sharing an Id (e.g. built via toBuilder()) keep their
    // values, matching the snapshot semantics the flat JPA fields had
    public void setConsensusTimestamp(long consensusTimestamp) {
        id = (id == null ? new Id() : id).withConsensusTimestamp(consensusTimestamp);
    }

    public void setContractId(EntityId entityId) {
        id = (id == null ? new Id() : id).withContractId(entityId.getId());
    }

    public void setContractId(long contractId) {
        id = (id == null ? new Id() : id).withContractId(contractId);
    }

    public void setSlot(byte[] slot) {
        id = (id == null ? new Id() : id).withSlot(slot);
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
    @With
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -3677350664183037811L;

        private long consensusTimestamp;

        private long contractId;

        private byte[] slot;
    }

    public static class ContractStateChangeBuilder {
        public ContractStateChangeBuilder consensusTimestamp(long consensusTimestamp) {
            this.id = (this.id == null ? new Id() : this.id).withConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public ContractStateChangeBuilder contractId(long contractId) {
            this.id = (this.id == null ? new Id() : this.id).withContractId(contractId);
            return this;
        }

        public ContractStateChangeBuilder slot(byte[] slot) {
            this.id = (this.id == null ? new Id() : this.id).withSlot(slot);
            return this;
        }
    }
}
