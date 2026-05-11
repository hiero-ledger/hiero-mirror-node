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
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("token_transfer")
@NoArgsConstructor
public class TokenTransfer implements Persistable<TokenTransfer.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private long amount;

    private Boolean isApproval;

    private EntityId payerAccountId;

    public TokenTransfer(long consensusTimestamp, long amount, EntityId tokenId, EntityId accountId) {
        this.id = new Id(consensusTimestamp, tokenId, accountId);
        this.amount = amount;
    }

    public static class TokenTransferBuilder {
        public TokenTransferBuilder consensusTimestamp(long consensusTimestamp) {
            initId();
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public TokenTransferBuilder tokenId(EntityId tokenId) {
            initId();
            this.id.setTokenId(tokenId);
            return this;
        }

        public TokenTransferBuilder accountId(EntityId accountId) {
            initId();
            this.id.setAccountId(accountId);
            return this;
        }

        private void initId() {
            if (this.id == null) {
                this.id = new Id();
            }
        }
    }

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public EntityId getTokenId() {
        return id != null ? id.getTokenId() : null;
    }

    public EntityId getAccountId() {
        return id != null ? id.getAccountId() : null;
    }

    @JsonIgnore
    @Override
    public Id getId() {
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder(toBuilder = true)
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 8693129287509470469L;

        private long consensusTimestamp;
        private EntityId tokenId;
        private EntityId accountId;
    }
}
