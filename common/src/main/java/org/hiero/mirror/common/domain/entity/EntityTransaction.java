// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("entity_transaction")
@NoArgsConstructor
public class EntityTransaction implements Persistable<EntityTransaction.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private EntityId payerAccountId;

    private Integer result;

    private Integer type;

    public static class EntityTransactionBuilder {
        public EntityTransactionBuilder consensusTimestamp(long consensusTimestamp) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public EntityTransactionBuilder entityId(long entityId) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setEntityId(entityId);
            return this;
        }
    }

    public Long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : null;
    }

    public Long getEntityId() {
        return id != null ? id.getEntityId() : null;
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

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = -3010905088908209508L;

        private long consensusTimestamp;
        private long entityId;
    }
}
