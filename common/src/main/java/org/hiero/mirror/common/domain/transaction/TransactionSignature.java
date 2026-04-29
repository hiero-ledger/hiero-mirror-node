// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Table("transaction_signature")
@NoArgsConstructor
public class TransactionSignature implements Persistable<TransactionSignature.Id> {

    @org.springframework.data.annotation.Id
    private long consensusTimestamp;

    // @Convert removed. Mapping is handled by the global EntityIdConverter bean.
    private EntityId entityId;

    @org.springframework.data.annotation.Id
    @ToString.Exclude
    private byte[] publicKeyPrefix;

    @ToString.Exclude
    private byte[] signature;

    private int type;

    @Override
    @JsonIgnore
    public TransactionSignature.Id getId() {
        return new TransactionSignature.Id(consensusTimestamp, publicKeyPrefix);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        // Keeps behavior consistent: always INSERT, skip the SELECT-before-SAVE.
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = -8758644338990079234L;
        private long consensusTimestamp;
        private byte[] publicKeyPrefix;
    }
}
