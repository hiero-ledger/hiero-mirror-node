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
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Table("contract_log")
@NoArgsConstructor
public class ContractLog implements Persistable<ContractLog.Id> {

    @ToString.Exclude
    private byte[] bloom;

    @org.springframework.data.annotation.Id
    private long consensusTimestamp;

    // Mapping handled by global EntityId converters registered in your configuration
    private EntityId contractId;

    @ToString.Exclude
    private byte[] data;

    @org.springframework.data.annotation.Id
    private int index;

    private EntityId rootContractId;

    private EntityId payerAccountId;

    private byte[] topic0;

    private byte[] topic1;

    private byte[] topic2;

    private byte[] topic3;

    private byte[] transactionHash;

    private int transactionIndex;

    private boolean synthetic;

    @Override
    @JsonIgnore
    public Id getId() {
        return new Id(consensusTimestamp, index);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        // Natural IDs for immutable logs should always trigger an INSERT
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = -6192177810161178246L;
        private long consensusTimestamp;
        private int index;
    }
}
