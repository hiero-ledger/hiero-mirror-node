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
import lombok.With;
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
    private Id id;

    private EntityId payerAccountId;

    private Integer result;

    private Integer type;

    public static class EntityTransactionBuilder {
        public EntityTransactionBuilder consensusTimestamp(long consensusTimestamp) {
            this.id = (this.id == null ? new Id() : this.id).withConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public EntityTransactionBuilder entityId(long entityId) {
            this.id = (this.id == null ? new Id() : this.id).withEntityId(entityId);
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
        return true; // Since we never update and use a natural ID, avoid Spring Data JDBC querying before insert
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    @With
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = -3010905088908209508L;

        private long consensusTimestamp;

        private long entityId;
    }
}
