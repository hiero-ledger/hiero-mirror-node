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
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder
@Data
@Table
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

    // Repeated sequence of payer_account_id, valid_start_ns
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> innerTransactions;

    private Long initialBalance;

    @JsonIgnore
    @Column("itemized_transfer")
    private ItemizedTransferListHolder itemizedTransferColumn;

    @JsonIgnore
    @Column("max_custom_fees")
    @ToString.Exclude
    private MaxCustomFeesHolder maxCustomFeesColumn;

    private Long maxFee;

    @ToString.Exclude
    private byte[] memo;

    @JsonIgnore
    @Column("nft_transfer")
    private NftTransferListHolder nftTransferColumn;

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

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<ItemizedTransfer> getItemizedTransfer() {
        return itemizedTransferColumn == null ? null : itemizedTransferColumn.items();
    }

    public void setItemizedTransfer(List<ItemizedTransfer> value) {
        this.itemizedTransferColumn = ItemizedTransferListHolder.of(value);
    }

    public byte[][] getMaxCustomFees() {
        return maxCustomFeesColumn == null ? null : maxCustomFeesColumn.items();
    }

    public void setMaxCustomFees(byte[][] value) {
        this.maxCustomFeesColumn = MaxCustomFeesHolder.of(value);
    }

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<NftTransfer> getNftTransfer() {
        return nftTransferColumn == null ? null : nftTransferColumn.items();
    }

    public void setNftTransfer(List<NftTransfer> value) {
        this.nftTransferColumn = NftTransferListHolder.of(value);
    }

    public void addItemizedTransfer(ItemizedTransfer itemizedTransfer) {
        if (itemizedTransfer == null) {
            return;
        }
        var transfers = getItemizedTransfer() == null
                ? new ArrayList<ItemizedTransfer>()
                : new ArrayList<>(getItemizedTransfer());
        transfers.add(itemizedTransfer);
        setItemizedTransfer(transfers);
    }

    public void addNftTransfer(NftTransfer nftTransfer) {
        if (nftTransfer == null) {
            return;
        }
        var transfers = getNftTransfer() == null ? new ArrayList<NftTransfer>() : new ArrayList<>(getNftTransfer());
        transfers.add(nftTransfer);
        setNftTransfer(transfers);
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
        return true; // Since we never update and use a natural ID, avoid Spring Data JDBC querying before insert
    }

    public TransactionHash toTransactionHash() {
        return TransactionHash.builder()
                .consensusTimestamp(consensusTimestamp)
                .hash(transactionHash)
                .payerAccountId(payerAccountId.getId())
                .build();
    }

    public static class TransactionBuilder {
        public TransactionBuilder itemizedTransfer(List<ItemizedTransfer> value) {
            this.itemizedTransferColumn = ItemizedTransferListHolder.of(value);
            return this;
        }

        public TransactionBuilder maxCustomFees(byte[][] value) {
            this.maxCustomFeesColumn = MaxCustomFeesHolder.of(value);
            return this;
        }

        public TransactionBuilder nftTransfer(List<NftTransfer> value) {
            this.nftTransferColumn = NftTransferListHolder.of(value);
            return this;
        }
    }
}
