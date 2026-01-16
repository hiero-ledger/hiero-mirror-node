// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.NftTransfer;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder
@Data
@Entity
@NoArgsConstructor
public class Transaction implements Persistable<Long> {

    @ToString.Exclude
    private byte[] batchKey;

    @Id
    private Long consensusTimestamp;

    private Long chargedTxFee;

    private EntityId entityId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ErrataType errata;

    private Integer index;

    // Repeated sequence of payer_account_id, valid_start_ns
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> innerTransactions;

    private Long initialBalance;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<ItemizedTransfer> itemizedTransfer;

    @ToString.Exclude
    private byte[][] maxCustomFees;

    private Long maxFee;

    @ToString.Exclude
    private byte[] memo;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<NftTransfer> nftTransfer;

    private EntityId nodeAccountId;

    private Integer nonce;

    private Long parentConsensusTimestamp;

    private EntityId payerAccountId;

    private Integer result;

    private boolean scheduled;

    @ToString.Exclude
    private byte[] transactionBytes;

    @ToString.Exclude
    private byte[] transactionHash;

    @ToString.Exclude
    private byte[] transactionRecordBytes;

    private Integer type;

    private Long validDurationSeconds;

    private Long validStartNs;

    public void addItemizedTransfer(@NonNull ItemizedTransfer itemizedTransfer) {
        if (this.itemizedTransfer == null) {
            this.itemizedTransfer = new ArrayList<>();
        }

        this.itemizedTransfer.add(itemizedTransfer);
    }

    public void addNftTransfer(@NonNull NftTransfer nftTransfer) {
        if (this.nftTransfer == null) {
            this.nftTransfer = new ArrayList<>();
        }

        this.nftTransfer.add(nftTransfer);
    }

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

    public void addInnerTransaction(Transaction transaction) {
        if (this.type != TransactionType.ATOMIC_BATCH.getProtoId()) {
            throw new IllegalStateException("Inner transactions can only be added to atomic batch transaction");
        }

        if (innerTransactions == null) {
            innerTransactions = new ArrayList<>();
        }

        innerTransactions.add(transaction.getPayerAccountId().getId());
        innerTransactions.add(transaction.getValidStartNs());
    }

    public TransactionHash toTransactionHash() {
        return TransactionHash.builder()
                .consensusTimestamp(consensusTimestamp)
                .hash(transactionHash)
                .payerAccountId(payerAccountId.getId())
                .build();
    }
}
