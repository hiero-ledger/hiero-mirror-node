// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.InsertOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@NoArgsConstructor
@Table
@Upsertable
public class ContractState {

    private static final int SLOT_BYTE_LENGTH = 32;

    @Transient
    private long contractId;

    @InsertOnlyProperty
    private long createdTimestamp;

    private long modifiedTimestamp;

    @Transient
    @ToString.Exclude
    private byte[] slot;

    @ToString.Exclude
    private byte[] value;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    public ContractState.Id getId() {
        ContractState.Id id = new ContractState.Id();
        id.setContractId(contractId);
        id.setSlot(slot);
        return id;
    }

    public void setId(ContractState.Id id) {
        if (id == null) {
            return;
        }

        this.contractId = id.getContractId();
        this.slot = id.getSlot();
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
