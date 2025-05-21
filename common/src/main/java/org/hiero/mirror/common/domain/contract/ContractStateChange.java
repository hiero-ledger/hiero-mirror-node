// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
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

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder(toBuilder = true)
@Data
@Entity
@IdClass(ContractStateChange.Id.class)
@NoArgsConstructor
public class ContractStateChange implements Persistable<ContractStateChange.Id> {

    @jakarta.persistence.Id
    private long consensusTimestamp;

    @jakarta.persistence.Id
    private long contractId;

    private boolean migration;

    private EntityId payerAccountId;

    @jakarta.persistence.Id
    @ToString.Exclude
    private byte[] slot;

    @ToString.Exclude
    private byte[] valueRead;

    @ToString.Exclude
    private byte[] valueWritten;

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
