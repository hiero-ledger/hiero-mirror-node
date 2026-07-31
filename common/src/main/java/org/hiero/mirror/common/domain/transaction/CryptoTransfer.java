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
import lombok.With;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table
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
            this.id = (this.id == null ? new Id() : this.id).withAmount(amount);
            return this;
        }

        public CryptoTransferBuilder consensusTimestamp(long consensusTimestamp) {
            this.id = (this.id == null ? new Id() : this.id).withConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public CryptoTransferBuilder entityId(long entityId) {
            this.id = (this.id == null ? new Id() : this.id).withEntityId(entityId);
            return this;
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
        id = (id == null ? new Id() : id).withAmount(amount);
    }

    public void setConsensusTimestamp(long consensusTimestamp) {
        id = (id == null ? new Id() : id).withConsensusTimestamp(consensusTimestamp);
    }

    public void setEntityId(long entityId) {
        id = (id == null ? new Id() : id).withEntityId(entityId);
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
    @With
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 6187276796581956587L;

        private long amount;

        private long consensusTimestamp;

        private long entityId;
    }
}
