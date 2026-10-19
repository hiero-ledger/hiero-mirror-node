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
@Table
@NoArgsConstructor
public class CryptoTransfer implements Persistable<CryptoTransfer.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    @JsonIgnore
    private Id id;

    private ErrataType errata;

    private Boolean isApproval;

    private EntityId payerAccountId;

    public static class CryptoTransferBuilder {

        private Id ensureId() {
            this.id = this.id == null
                    ? new Id()
                    : new Id(this.id.getAmount(), this.id.getConsensusTimestamp(), this.id.getEntityId());
            return this.id;
        }

        public CryptoTransferBuilder amount(long amount) {
            ensureId().setAmount(amount);
            return this;
        }

        public CryptoTransferBuilder consensusTimestamp(long consensusTimestamp) {
            ensureId().setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public CryptoTransferBuilder entityId(long entityId) {
            ensureId().setEntityId(entityId);
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
        id().setAmount(amount);
    }

    public void setConsensusTimestamp(long consensusTimestamp) {
        id().setConsensusTimestamp(consensusTimestamp);
    }

    public void setEntityId(long entityId) {
        id().setEntityId(entityId);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid querying before insert
    }

    /*
     * It used to be that crypto transfers could have multiple amounts for the same account, so all fields were used for
     * uniqueness. Later a change was made to aggregate amounts by account making the unique key
     * (consensusTimestamp, entityId). Since we didn't migrate the old data to aggregate we have to treat all fields as
     * the key still.
     */
    private Id id() {
        if (id == null) {
            id = new Id();
        }
        return id;
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
