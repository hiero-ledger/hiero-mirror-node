// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@EqualsAndHashCode(exclude = "contractResult")
@Entity
@IdClass(ContractLog.Id.class)
@NoArgsConstructor
public class ContractLog implements Persistable<ContractLog.Id> {

    @ToString.Exclude
    private byte[] bloom;

    @jakarta.persistence.Id
    private long consensusTimestamp;

    private EntityId contractId;

    @ToString.Exclude
    private byte[] data;

    @jakarta.persistence.Id
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

    /**
     * Transient reference to the ContractResult this log belongs to.
     * Used for updating the bloom filter in the correct ContractResult during synthetic log processing.
     */
    @JsonIgnore
    @Transient
    @ToString.Exclude
    private ContractResult contractResult;

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
            final var bloomFilter = new LogsBloomFilter();
            final var existingResultBloom = contractResult.getBloom();
            if (existingResultBloom != null && !Arrays.equals(ArrayUtils.EMPTY_BYTE_ARRAY, existingResultBloom)) {
                bloomFilter.or(existingResultBloom);
            }

            bloomFilter.or(bloom);
            contractResult.setBloom(bloomFilter.toArrayUnsafe());
        }
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
