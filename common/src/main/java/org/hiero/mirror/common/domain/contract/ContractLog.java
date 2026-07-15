// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.With;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.InsertOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@EqualsAndHashCode(exclude = "contractResult")
@NoArgsConstructor
@Table("contract_log")
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

    /**
     * Transient reference to the ContractResult this log belongs to.
     * Used for updating the bloom filter in the correct ContractResult during synthetic log processing.
     */
    @JsonIgnore
    @Transient
    @ToString.Exclude
    private ContractResult contractResult;

    public void setConsensusTimestamp(long consensusTimestamp) {
        id = (id == null ? new Id() : id).withConsensusTimestamp(consensusTimestamp);
    }

    public void setIndex(int index) {
        id = (id == null ? new Id() : id).withIndex(index);
    }

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public int getIndex() {
        return id != null ? id.getIndex() : 0;
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

    public void setBloom(final byte[] bloom) {
        if (bloom == null) {
            return;
        }

        this.bloom = bloom;
        if (synthetic && contractResult != null) {
            final var existingResultBloom = contractResult.getBloom();
            final var aggregatedBloom = bloom.length == LogsBloomFilter.BYTE_SIZE
                    ? LogsBloomFilter.or(existingResultBloom, bloom)
                    : existingResultBloom;

            contractResult.setBloom(aggregatedBloom);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @With
    public static class Id implements Serializable {
        private static final long serialVersionUID = -6192177810161178246L;

        @InsertOnlyProperty
        private long consensusTimestamp;

        @InsertOnlyProperty
        private int index;
    }

    public static class ContractLogBuilder {
        public ContractLogBuilder consensusTimestamp(long consensusTimestamp) {
            this.id = (this.id == null ? new Id() : this.id).withConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public ContractLogBuilder index(int index) {
            this.id = (this.id == null ? new Id() : this.id).withIndex(index);
            return this;
        }
    }
}
