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
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@Table
public class ContractStateChange implements Persistable<ContractStateChange.Id> {

    @Transient
    private long consensusTimestamp;

    @Transient
    private long contractId;

    private boolean migration;

    private EntityId payerAccountId;

    @Transient
    @ToString.Exclude
    private byte[] slot;

    @ToString.Exclude
    private byte[] valueRead;

    @ToString.Exclude
    private byte[] valueWritten;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @Override
    @JsonIgnore
    public Id getId() {
        Id id = new Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setContractId(contractId);
        id.setSlot(slot);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    public void setContractId(EntityId contractId) {
        this.contractId = contractId.getId();
    }

    public void setId(Id id) {
        if (id == null) {
            return;
        }

        this.consensusTimestamp = id.getConsensusTimestamp();
        this.contractId = id.getContractId();
        this.slot = id.getSlot();
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
}
