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
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("contract_state_change")
@NoArgsConstructor
public class ContractStateChange implements Persistable<ContractStateChange.Id> {

    @org.springframework.data.annotation.Id
    private long consensusTimestamp;

    @org.springframework.data.annotation.Id
    private long contractId;

    private boolean migration;

    // Converter is now managed globally by your JdbcCustomConversions configuration
    private EntityId payerAccountId;

    @org.springframework.data.annotation.Id
    @ToString.Exclude
    private byte[] slot;

    @ToString.Exclude
    private byte[] valueRead;

    @ToString.Exclude
    private byte[] valueWritten;

    @Override
    @JsonIgnore
    public Id getId() {
        return new Id(consensusTimestamp, contractId, slot);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        // Keeps the optimization to skip the "SELECT" check before insert
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
