// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("contract_result")
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

    // JDBC: Requires a custom Reading/Writing converter if stored as a DB array or CSV string
    @Builder.Default
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> createdContractIds = Collections.emptyList();

    private String errorMessage;

    @ToString.Exclude
    private byte[] failedInitcode;

    @ToString.Exclude
    private byte[] functionParameters;

    private byte[] functionResult;

    private Long gasConsumed;

    private Long gasLimit;

    private Long gasUsed;

    // Handled by global EntityId converters
    private EntityId payerAccountId;

    // Handled by global EntityId converters
    private EntityId senderId;

    private byte[] transactionHash;

    private Integer transactionIndex;

    private int transactionNonce;

    private Integer transactionResult;

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
        return true;
    }

    public ContractTransactionHash toContractTransactionHash() {
        return ContractTransactionHash.builder()
                .consensusTimestamp(consensusTimestamp)
                .hash(transactionHash)
                .entityId(contractId)
                .payerAccountId(payerAccountId != null ? payerAccountId.getId() : null)
                .transactionResult(transactionResult)
                .build();
    }
}
