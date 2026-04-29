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
import org.springframework.data.annotation.Id;
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
    private long contractId;

    // Hibernate's 'updatable = false' is removed;
    // enforcement now relies on the Upsert logic/SQL generator.
    private long createdTimestamp;

    private long modifiedTimestamp;

    @org.springframework.data.annotation.Id
    @ToString.Exclude
    private byte[] slot;

    @ToString.Exclude
    private byte[] value;

    @JsonIgnore
    public ContractState.Id getId() {
        return new Id(contractId, slot);
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
