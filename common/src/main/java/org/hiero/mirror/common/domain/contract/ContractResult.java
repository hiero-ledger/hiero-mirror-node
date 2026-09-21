// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.data.domain.Persistable;

@Data
@Entity
@NoArgsConstructor
@SuperBuilder
public class ContractResult implements Persistable<Long> {

    private static final byte[] EMPTY_BLOOM = new byte[256];

    private Long amount;

    @ToString.Exclude
    private byte[] bloom;

    @ToString.Exclude
    private byte[] callResult;

    @Id
    private Long consensusTimestamp;

    private long contractId;

    @Builder.Default
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> createdContractIds = Collections.emptyList();

    private String errorMessage;

    @ToString.Exclude
    private byte[] failedInitcode;

    @ToString.Exclude
    private byte[] functionParameters;

    private byte[] functionResult; // Temporary field until we can confirm the migration captured everything

    private Long gasConsumed;

    private Long gasLimit;

    private Long gasUsed;

    @Convert(converter = EntityIdConverter.class)
    private EntityId payerAccountId;

    @Convert(converter = EntityIdConverter.class)
    private EntityId senderId;

    private byte[] transactionHash;

    private Integer transactionIndex;

    private int transactionNonce;

    private Integer transactionResult;

    /**
     * Whether this result should be skipped when indexing contract_transaction_hash. Results for ethereum
     * transactions that failed before execution set this to true: they have no matching contract_transaction row, so a
     * hash mapping to them can never resolve and would only shadow the genuine execution sharing the same hash. The
     * default {@code false} keeps indexing enabled for both the builder and {@code new ContractResult()}. Transient
     * processing hint, never persisted.
     */
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    @ToString.Exclude
    @Transient
    private boolean contractTransactionHashSkipped;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    public void setBloom(byte[] bloom) {
        this.bloom = !Arrays.equals(bloom, EMPTY_BLOOM) ? bloom : ArrayUtils.EMPTY_BYTE_ARRAY;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = DomainUtils.sanitize(errorMessage);
    }

    public ContractTransactionHash toContractTransactionHash() {
        return ContractTransactionHash.builder()
                .consensusTimestamp(consensusTimestamp)
                .hash(transactionHash)
                .entityId(contractId)
                .payerAccountId(payerAccountId.getId())
                .transactionResult(transactionResult)
                .build();
    }

    public abstract static class ContractResultBuilder<
            C extends ContractResult, B extends ContractResultBuilder<C, B>> {
        public B errorMessage(String errorMessage) {
            this.errorMessage = DomainUtils.sanitize(errorMessage);
            return self();
        }
    }
}
