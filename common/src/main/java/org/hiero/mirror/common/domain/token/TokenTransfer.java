// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@NoArgsConstructor
public class TokenTransfer implements Persistable<TokenTransfer.Id> {

    @EmbeddedId
    @JsonUnwrapped
    private Id id;

    private long amount;

    private Boolean isApproval;

    private EntityId payerAccountId;

    public TokenTransfer(long consensusTimestamp, long amount, EntityId tokenId, EntityId accountId) {
        id = new TokenTransfer.Id(consensusTimestamp, tokenId, accountId);
        this.amount = amount;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Builder(toBuilder = true)
    @Data
    @Embeddable
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
