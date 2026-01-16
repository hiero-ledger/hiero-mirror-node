// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Entity
@IdClass(CryptoTransfer.Id.class)
@NoArgsConstructor
public class CryptoTransfer implements Persistable<CryptoTransfer.Id> {

    @jakarta.persistence.Id
    private long amount;

    @jakarta.persistence.Id
    private long consensusTimestamp;

    @jakarta.persistence.Id
    private long entityId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ErrataType errata;

    private Boolean isApproval;

    private EntityId payerAccountId;

    @JsonIgnore
    @Override
    public Id getId() {
        Id id = new Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setAmount(amount);
        id.setEntityId(entityId);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    /*
     * It used to be that crypto transfers could have multiple amounts for the same account, so all fields were used for
     * uniqueness. Later a change was made to aggregate amounts by account making the unique key
     * (consensusTimestamp, entityId). Since we didn't migrate the old data to aggregate we have to treat all fields as
     * the key still.
     */
    @Data
    @Embeddable
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = 6187276796581956587L;

        private long amount;
        private long consensusTimestamp;
        private long entityId;
    }
}
