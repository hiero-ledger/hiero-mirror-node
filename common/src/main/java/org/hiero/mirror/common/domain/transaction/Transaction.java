// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.NftTransfer;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Table("transaction")
@NoArgsConstructor
public class Transaction implements Persistable<Long> {

    @ToString.Exclude
    private byte[] batchKey;

    private Long chargedTxFee;

    private Long congestionPricingMultiplier;

    @Id
    private Long consensusTimestamp;

    private EntityId entityId;

    private ErrataType errata;

    private Boolean highVolume;

    private Long highVolumePricingMultiplier;

    private Integer index;

    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> innerTransactions;

    private Long initialBalance;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    private List<ItemizedTransfer> itemizedTransfer;

    @ToString.Exclude
    private byte[][] maxCustomFees;

    private Long maxFee;

    @ToString.Exclude
    private byte[] memo;

    @JsonSerialize(using = ObjectToStringSerializer.class)
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

    public void addItemizedTransfer(ItemizedTransfer itemizedTransfer) {
        if (itemizedTransfer == null) {
            return;
        }
        if (this.itemizedTransfer == null) {
            this.itemizedTransfer = new ArrayList<>();
        } else if (!(this.itemizedTransfer instanceof ArrayList)) {
            this.itemizedTransfer = new ArrayList<>(this.itemizedTransfer);
        }
        this.itemizedTransfer.add(itemizedTransfer);
    }

    public void addNftTransfer(NftTransfer nftTransfer) {
        if (nftTransfer == null) {
            return;
        }
        if (this.nftTransfer == null) {
            this.nftTransfer = new ArrayList<>();
        } else if (!(this.nftTransfer instanceof ArrayList)) {
            this.nftTransfer = new ArrayList<>(this.nftTransfer);
        }
        this.nftTransfer.add(nftTransfer);
    }

    public void addInnerTransaction(Transaction innerTransaction) {
        if (type == null || !type.equals(TransactionType.ATOMIC_BATCH.getProtoId())) {
            throw new IllegalStateException("Inner transactions can only be added to atomic batch transaction");
        }
        if (innerTransaction == null) {
            return;
        }
        if (innerTransactions == null) {
            innerTransactions = new ArrayList<>();
        } else if (!(innerTransactions instanceof ArrayList)) {
            innerTransactions = new ArrayList<>(innerTransactions);
        }
        innerTransactions.add(innerTransaction.getPayerAccountId().getId());
        innerTransactions.add(innerTransaction.getValidStartNs());
    }

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    public TransactionHash toTransactionHash() {
        return TransactionHash.builder()
                .consensusTimestamp(consensusTimestamp)
                .hash(transactionHash)
                .payerAccountId(payerAccountId != null ? payerAccountId.getId() : 0L)
                .build();
    }
}
