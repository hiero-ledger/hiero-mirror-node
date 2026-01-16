// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Entity
@IdClass(AssessedCustomFee.Id.class)
@NoArgsConstructor
public class AssessedCustomFee implements Persistable<AssessedCustomFee.Id> {

    private long amount;

    @jakarta.persistence.Id
    private long collectorAccountId;

    @jakarta.persistence.Id
    private long consensusTimestamp;

    @Builder.Default
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> effectivePayerAccountIds = Collections.emptyList();

    private EntityId tokenId;

    private EntityId payerAccountId;

    @JsonIgnore
    @Override
    public Id getId() {
        var id = new Id();
        id.setCollectorAccountId(collectorAccountId);
        id.setConsensusTimestamp(consensusTimestamp);
        return id;
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
        private static final long serialVersionUID = -636368167561206418L;

        private long collectorAccountId;

        private long consensusTimestamp;
    }
}
