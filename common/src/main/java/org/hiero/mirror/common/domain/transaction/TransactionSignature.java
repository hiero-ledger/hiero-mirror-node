// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@IdClass(TransactionSignature.Id.class)
@NoArgsConstructor
public class TransactionSignature implements Persistable<TransactionSignature.Id> {

    @jakarta.persistence.Id
    private long consensusTimestamp;

    private EntityId entityId;

    @jakarta.persistence.Id
    @ToString.Exclude
    private byte[] publicKeyPrefix;

    @ToString.Exclude
    private byte[] signature;

    private int type;

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

    @Data
    public static class Id implements Serializable {
        private static final long serialVersionUID = -8758644338990079234L;
        private long consensusTimestamp;
        private byte[] publicKeyPrefix;
    }
}
