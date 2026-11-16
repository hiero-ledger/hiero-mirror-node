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
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@NoArgsConstructor
@Table
public class TransactionSignature implements Persistable<TransactionSignature.Id> {

    @Transient
    private long consensusTimestamp;

    private EntityId entityId;

    @Transient
    @ToString.Exclude
    private byte[] publicKeyPrefix;

    @ToString.Exclude
    private byte[] signature;

    private int type;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @Override
    @JsonIgnore
    public TransactionSignature.Id getId() {
        TransactionSignature.Id transactionSignatureId = new TransactionSignature.Id();
        transactionSignatureId.setConsensusTimestamp(consensusTimestamp);
        transactionSignatureId.setPublicKeyPrefix(publicKeyPrefix);
        return transactionSignatureId;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    public void setId(TransactionSignature.Id id) {
        if (id == null) {
            return;
        }

        this.consensusTimestamp = id.getConsensusTimestamp();
        this.publicKeyPrefix = id.getPublicKeyPrefix();
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = -8758644338990079234L;
        private long consensusTimestamp;
        private byte[] publicKeyPrefix;
    }
}
