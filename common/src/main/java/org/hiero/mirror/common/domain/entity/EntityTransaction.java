// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.entity.EntityTransaction.Id;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@IdClass(EntityTransaction.Id.class)
@NoArgsConstructor
public class EntityTransaction implements Persistable<Id> {

    @Column(updatable = false)
    @jakarta.persistence.Id
    private Long consensusTimestamp;

    @Column(updatable = false)
    @jakarta.persistence.Id
    private Long entityId;

    @Convert(converter = EntityIdConverter.class)
    @Column(updatable = false)
    private EntityId payerAccountId;

    @Column(updatable = false)
    private Integer result;

    @Column(updatable = false)
    private Integer type;

    public void setResult(Integer result) {
        this.result = DomainUtils.toSmallint(result);
    }

    public void setType(Integer type) {
        this.type = DomainUtils.toSmallint(type);
    }

    @JsonIgnore
    @Override
    public Id getId() {
        return new Id(consensusTimestamp, entityId);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
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
