// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder(toBuilder = true)
@Data
@Table
@NoArgsConstructor
public class TransactionSignature implements Persistable<TransactionSignature.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    @JsonIgnore
    private Id id;

    private EntityId entityId;

    @ToString.Exclude
    private byte[] signature;

    private int type;

    public static class TransactionSignatureBuilder {
        public TransactionSignatureBuilder consensusTimestamp(long consensusTimestamp) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public TransactionSignatureBuilder publicKeyPrefix(byte[] publicKeyPrefix) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setPublicKeyPrefix(publicKeyPrefix);
            return this;
        }
    }

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public byte[] getPublicKeyPrefix() {
        return id != null ? id.getPublicKeyPrefix() : null;
    }

    public void setConsensusTimestamp(long consensusTimestamp) {
        if (id == null) {
            id = new Id();
        }
        id.setConsensusTimestamp(consensusTimestamp);
    }

    public void setPublicKeyPrefix(byte[] publicKeyPrefix) {
        if (id == null) {
            id = new Id();
        }
        id.setPublicKeyPrefix(publicKeyPrefix);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Force INSERT for performance
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = -8758644338990079234L;

        private long consensusTimestamp;

        private byte[] publicKeyPrefix;
    }
}
