// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder(toBuilder = true)
@Data
@Table("token_transfer")
@NoArgsConstructor
public class TokenTransfer implements Persistable<TokenTransfer.Id> {

    @org.springframework.data.annotation.Id
    private long consensusTimestamp;

    @org.springframework.data.annotation.Id
    private EntityId tokenId;

    @org.springframework.data.annotation.Id
    private EntityId accountId;

    private long amount;

    private Boolean isApproval;

    // Converter removed. Handled by global EntityId Reading/Writing converters.
    private EntityId payerAccountId;

    public TokenTransfer(long consensusTimestamp, long amount, EntityId tokenId, EntityId accountId) {
        this.consensusTimestamp = consensusTimestamp;
        this.tokenId = tokenId;
        this.accountId = accountId;
        this.amount = amount;
    }

    @JsonIgnore
    @Override
    public Id getId() {
        return new Id(consensusTimestamp, tokenId, accountId);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Bypasses the "Select before Insert" existence check
    }

    @Builder(toBuilder = true)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 8693129287509470469L;

        private long consensusTimestamp;
        private EntityId tokenId;
        private EntityId accountId;
    }
}
