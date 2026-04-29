// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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

    // Handled by global EntityIdConverter
    private EntityId entityId;

    // Handled by global ErrataType converter (if custom PG type)
    private ErrataType errata;

    private Boolean highVolume;

    private Long highVolumePricingMultiplier;

    private Integer index;

    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> innerTransactions;

    private Long initialBalance;

    // JDBC: Requires custom JSON Reading/Writing converters
    @JsonSerialize(using = ObjectToStringSerializer.class)
    private List<ItemizedTransfer> itemizedTransfer;

    @ToString.Exclude
    private byte[][] maxCustomFees;

    private Long maxFee;

    @ToString.Exclude
    private byte[] memo;

    // JDBC: Requires custom JSON Reading/Writing converters
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

    // ... Methods (addItemizedTransfer, addNftTransfer, etc.) remain unchanged ...

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

    // ... Logic methods (addInnerTransaction, toTransactionHash) remain unchanged ...
}
