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
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@EqualsAndHashCode(exclude = "contractResult")
@NoArgsConstructor
@Table
public class ContractLog implements Persistable<ContractLog.Id> {

    @ToString.Exclude
    private byte[] bloom;

    @Transient
    private long consensusTimestamp;

    private EntityId contractId;

    @ToString.Exclude
    private byte[] data;

    @Transient
    private int index;

    private EntityId rootContractId;

    private EntityId payerAccountId;

    private byte[] topic0;

    private byte[] topic1;

    private byte[] topic2;

    private byte[] topic3;

    private byte[] transactionHash;

    private Integer transactionIndex;

    private boolean synthetic;

    /**
     * Transient reference to the ContractResult this log belongs to.
     * Used for updating the bloom filter in the correct ContractResult during synthetic log processing.
     */
    @JsonIgnore
    @Transient
    @ToString.Exclude
    private ContractResult contractResult;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @Override
    @JsonIgnore
    public Id getId() {
        Id id = new Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setIndex(index);
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

    public void setId(Id id) {
        if (id == null) {
            return;
        }

        this.consensusTimestamp = id.getConsensusTimestamp();
        this.index = id.getIndex();
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
