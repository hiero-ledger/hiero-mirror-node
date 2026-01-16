// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.google.common.collect.Range;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true, skipPartialUpdate = true)
public class AbstractToken implements History {

    @Column(updatable = false)
    private Long createdTimestamp;

    @Column(updatable = false)
    private Integer decimals;

    @ToString.Exclude
    private byte[] feeScheduleKey;

    @Column(updatable = false)
    private Boolean freezeDefault;

    @ToString.Exclude
    private byte[] freezeKey;

    @Enumerated(EnumType.ORDINAL)
    @Column(updatable = false)
    private TokenFreezeStatusEnum freezeStatus;

    @Column(updatable = false)
    private Long initialSupply;

    @ToString.Exclude
    private byte[] kycKey;

    @Enumerated(EnumType.ORDINAL)
    @Column(updatable = false)
    private TokenKycStatusEnum kycStatus;

    @Column(updatable = false)
    private long maxSupply;

    @ToString.Exclude
    private byte[] metadata;

    @ToString.Exclude
    private byte[] metadataKey;

    private String name;

    @ToString.Exclude
    private byte[] pauseKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TokenPauseStatusEnum pauseStatus;

    @ToString.Exclude
    private byte[] supplyKey;

    @Column(updatable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TokenSupplyTypeEnum supplyType;

    private String symbol;

    private Range<Long> timestampRange;

    @Id
    private Long tokenId;

    @UpsertColumn(coalesce = "case when {0} >= 0 then {0} else e_{0} + coalesce({0}, {1}) end")
    private Long totalSupply; // Increment with initialSupply and mint amounts, decrement with burn amount

    private EntityId treasuryAccountId;

    @Column(updatable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TokenTypeEnum type;

    @ToString.Exclude
    private byte[] wipeKey;

    public void setName(String name) {
        this.name = DomainUtils.sanitize(name);
    }

    public void setSymbol(String symbol) {
        this.symbol = DomainUtils.sanitize(symbol);
    }

    public void setTotalSupply(Long newTotalSupply) {
        if (newTotalSupply == null) {
            return;
        }

        if (newTotalSupply < 0) {
            // Negative from a token transfer of a token dissociate of a deleted token, so we aggregate the change.
            totalSupply = totalSupply == null ? newTotalSupply : totalSupply + newTotalSupply;
        } else {
            totalSupply = newTotalSupply;
        }
    }
}
