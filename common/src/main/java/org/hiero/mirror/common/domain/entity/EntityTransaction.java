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
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Table("entity_transaction")
@NoArgsConstructor
public class EntityTransaction implements Persistable<Id> {

    @org.springframework.data.relational.core.mapping.Column
    @org.springframework.data.annotation.Id
    private Long consensusTimestamp;

    @org.springframework.data.relational.core.mapping.Column
    @org.springframework.data.annotation.Id
    private Long entityId;

    // Specify converter explicitly so translation works with native image
    //    @Convert(converter = EntityIdConverter.class)
    @org.springframework.data.relational.core.mapping.Column
    private EntityId payerAccountId;

    @org.springframework.data.relational.core.mapping.Column
    private Integer result;

    @org.springframework.data.relational.core.mapping.Column
    private Integer type;

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
