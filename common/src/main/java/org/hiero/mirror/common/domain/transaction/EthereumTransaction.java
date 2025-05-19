// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder
@Data
@Entity
@NoArgsConstructor
public class EthereumTransaction implements Persistable<Long> {

    @ToString.Exclude
    private byte[] accessList;

    @ToString.Exclude
    private byte[] callData;

    private EntityId callDataId;

    @ToString.Exclude
    private byte[] chainId;

    @Id
    private long consensusTimestamp;

    @ToString.Exclude
    private byte[] data;

    // persisted in tinybar
    private Long gasLimit;

    // persisted in tinybar
    private byte[] gasPrice;

    @ToString.Exclude
    private byte[] hash;

    // persisted in tinybar
    private byte[] maxFeePerGas;

    // persisted in tinybar
    private Long maxGasAllowance;

    // persisted in tinybar
    private byte[] maxPriorityFeePerGas;

    private Long nonce;

    private EntityId payerAccountId;

    private Integer recoveryId;

    @Column(name = "signature_r")
    @ToString.Exclude
    private byte[] signatureR;

    @Column(name = "signature_s")
    @ToString.Exclude
    private byte[] signatureS;

    @Column(name = "signature_v")
    @ToString.Exclude
    private byte[] signatureV;

    @ToString.Exclude
    private byte[] toAddress;

    private Integer type;

    @ToString.Exclude
    private byte[] value;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    public TransactionHash toTransactionHash() {
        return TransactionHash.builder()
                .consensusTimestamp(consensusTimestamp)
                .hash(hash)
                .payerAccountId(payerAccountId.getId())
                .build();
    }
}
