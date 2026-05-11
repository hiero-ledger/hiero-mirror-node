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
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("contract_state")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@NoArgsConstructor
@Upsertable
public class ContractState {

    private static final int SLOT_BYTE_LENGTH = 32;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private long createdTimestamp;

    private long modifiedTimestamp;

    @ToString.Exclude
    private byte[] value;

    public void setContractId(long contractId) {
        if (id == null) {
            id = new Id();
        }
        id.setContractId(contractId);
    }

    public void setSlot(byte[] slot) {
        if (id == null) {
            id = new Id();
        }
        id.setSlot(DomainUtils.leftPadBytes(slot, SLOT_BYTE_LENGTH));
    }

    public long getContractId() {
        return id != null ? id.getContractId() : 0L;
    }

    public byte[] getSlot() {
        return id != null ? id.getSlot() : null;
    }

    @JsonIgnore
    public Id getId() {
        return id;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = 6192377810161178246L;
        private long contractId;
        private byte[] slot;
    }

    public static class ContractStateBuilder {
        public ContractStateBuilder contractId(long contractId) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setContractId(contractId);
            return this;
        }

        public ContractStateBuilder slot(byte[] slot) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setSlot(DomainUtils.leftPadBytes(slot, SLOT_BYTE_LENGTH));
            return this;
        }
    }
}
