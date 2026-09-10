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
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder(toBuilder = true)
@Data
@Table
@NoArgsConstructor
public class EntityTransaction implements Persistable<EntityTransaction.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    @JsonIgnore
    private Id id;

    private EntityId payerAccountId;

    private Integer result;

    private Integer type;

    public void setResult(Integer result) {
        this.result = DomainUtils.toSmallint(result);
    }

    public void setType(Integer type) {
        this.type = DomainUtils.toSmallint(type);
    }

    public Long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : null;
    }

    public Long getEntityId() {
        return id != null ? id.getEntityId() : null;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid querying before insert
    }

    public static class EntityTransactionBuilder {

        private Id ensureId() {
            if (this.id == null) {
                this.id = new Id();
            }
            return this.id;
        }

        public EntityTransactionBuilder consensusTimestamp(long consensusTimestamp) {
            ensureId().setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public EntityTransactionBuilder entityId(long entityId) {
            ensureId().setEntityId(entityId);
            return this;
        }

        public EntityTransactionBuilder result(Integer result) {
            this.result = DomainUtils.toSmallint(result);
            return this;
        }

        public EntityTransactionBuilder type(Integer type) {
            this.type = DomainUtils.toSmallint(type);
            return this;
        }

        public EntityTransaction build() {
            final var builtId = id == null ? null : new Id(id.getConsensusTimestamp(), id.getEntityId());
            return new EntityTransaction(builtId, payerAccountId, result, type);
        }
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
