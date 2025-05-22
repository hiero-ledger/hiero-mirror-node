// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.util.DomainUtils;

@Data
@Entity
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@IdClass(ContractState.Id.class)
@NoArgsConstructor
@Upsertable
public class ContractState {

    private static final int SLOT_BYTE_LENGTH = 32;

    @jakarta.persistence.Id
    private long contractId;

    @Column(updatable = false)
    private long createdTimestamp;

    private long modifiedTimestamp;

    @jakarta.persistence.Id
    @ToString.Exclude
    private byte[] slot;

    @ToString.Exclude
    private byte[] value;

    @JsonIgnore
    public ContractState.Id getId() {
        ContractState.Id id = new ContractState.Id();
        id.setContractId(contractId);
        id.setSlot(slot);
        return id;
    }

    public void setSlot(byte[] slot) {
        this.slot = DomainUtils.leftPadBytes(slot, SLOT_BYTE_LENGTH);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = 6192377810161178246L;
        private long contractId;
        private byte[] slot;
    }
}
