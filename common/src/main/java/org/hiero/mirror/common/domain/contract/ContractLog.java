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
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
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
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private EntityId contractId;

    @ToString.Exclude
    private byte[] data;

    private EntityId rootContractId;

    private EntityId payerAccountId;

    private byte[] topic0;

    private byte[] topic1;

    private byte[] topic2;

    private byte[] topic3;

    private byte[] transactionHash;

    private int transactionIndex;

    private boolean synthetic;

    public void setConsensusTimestamp(long consensusTimestamp) {
        if (id == null) {
            id = new Id();
        }
        id.setConsensusTimestamp(consensusTimestamp);
    }

    public void setIndex(int index) {
        if (id == null) {
            id = new Id();
        }
        id.setIndex(index);
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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = -6192177810161178246L;
        private long consensusTimestamp;
        private int index;
    }

    public static class ContractLogBuilder {
        public ContractLogBuilder consensusTimestamp(long consensusTimestamp) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public ContractLogBuilder index(int index) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setIndex(index);
            return this;
        }
    }
}
