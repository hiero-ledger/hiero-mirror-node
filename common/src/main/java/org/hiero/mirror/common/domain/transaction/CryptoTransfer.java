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
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("crypto_transfer")
@NoArgsConstructor
public class CryptoTransfer implements Persistable<CryptoTransfer.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private ErrataType errata;

    private Boolean isApproval;

    private EntityId payerAccountId;

    public static class CryptoTransferBuilder {
        public CryptoTransferBuilder amount(long amount) {
            initId();
            this.id.setAmount(amount);
            return this;
        }

        public CryptoTransferBuilder consensusTimestamp(long consensusTimestamp) {
            initId();
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public CryptoTransferBuilder entityId(long entityId) {
            initId();
            this.id.setEntityId(entityId);
            return this;
        }

        private void initId() {
            if (this.id == null) {
                this.id = new Id();
            }
        }
    }

    public long getAmount() {
        return id != null ? id.getAmount() : 0L;
    }

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public long getEntityId() {
        return id != null ? id.getEntityId() : 0L;
    }

    public void setAmount(long amount) {
        initId();
        id.setAmount(amount);
    }

    public void setConsensusTimestamp(long consensusTimestamp) {
        initId();
        id.setConsensusTimestamp(consensusTimestamp);
    }

    public void setEntityId(long entityId) {
        initId();
        id.setEntityId(entityId);
    }

    private void initId() {
        if (id == null) {
            id = new Id();
        }
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
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 6187276796581956587L;

        private long amount;
        private long consensusTimestamp;
        private long entityId;
    }
}
