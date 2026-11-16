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
import org.hiero.mirror.common.domain.entity.EntityTransaction.Id;
import org.hiero.mirror.common.util.DomainUtils;
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
public class EntityTransaction implements Persistable<Id> {

    @Transient
    private Long consensusTimestamp;

    @Transient
    private Long entityId;

    private EntityId payerAccountId;

    private Integer result;

    private Integer type;

    public void setId(Id id) {
        if (id == null) {
            return;
        }

        this.consensusTimestamp = id.getConsensusTimestamp();
        this.entityId = id.getEntityId();
    }

    public void setResult(Integer result) {
        this.result = DomainUtils.toSmallint(result);
    }

    public void setType(Integer type) {
        this.type = DomainUtils.toSmallint(type);
    }

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    @Override
    public Id getId() {
        return new Id(consensusTimestamp, entityId);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid querying before insert
    }

    public static class EntityTransactionBuilder {
        public EntityTransactionBuilder result(Integer result) {
            this.result = DomainUtils.toSmallint(result);
            return this;
        }

        public EntityTransactionBuilder type(Integer type) {
            this.type = DomainUtils.toSmallint(type);
            return this;
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
