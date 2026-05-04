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

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("transaction_signature")
@NoArgsConstructor
public class TransactionSignature implements Persistable<TransactionSignature.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private EntityId entityId;

    @ToString.Exclude
    private byte[] signature;

    private int type;

    public static class TransactionSignatureBuilder {
        public TransactionSignatureBuilder consensusTimestamp(long consensusTimestamp) {
            initId();
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public TransactionSignatureBuilder publicKeyPrefix(byte[] publicKeyPrefix) {
            initId();
            this.id.setPublicKeyPrefix(publicKeyPrefix);
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

    public byte[] getPublicKeyPrefix() {
        return id != null ? id.getPublicKeyPrefix() : null;
    }

    @Override
    @JsonIgnore
    public Id getId() {
        return id;
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
