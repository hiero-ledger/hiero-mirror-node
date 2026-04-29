// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Table("crypto_transfer")
@NoArgsConstructor
public class CryptoTransfer implements Persistable<CryptoTransfer.Id> {

    @org.springframework.data.annotation.Id
    private long amount;

    @org.springframework.data.annotation.Id
    private long consensusTimestamp;

    @org.springframework.data.annotation.Id
    private long entityId;

    // No @Enumerated or @JdbcTypeCode needed.
    // Spring Data JDBC handles enums via registered Converters or standard string mapping.
    private ErrataType errata;

    private Boolean isApproval;

    // Ensure your EntityId converters (from the previous step) are registered
    // in your AbstractJdbcConfiguration.
    private EntityId payerAccountId;

    @JsonIgnore
    @Override
    public Id getId() {
        return new Id(amount, consensusTimestamp, entityId);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = 6187276796581956587L;
        private long amount;
        private long consensusTimestamp;
        private long entityId;
    }
}
