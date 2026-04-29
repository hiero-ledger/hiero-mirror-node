// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Table("assessed_custom_fee")
@NoArgsConstructor
public class AssessedCustomFee implements Persistable<AssessedCustomFee.Id> {

    private long amount;

    @org.springframework.data.annotation.Id
    private long collectorAccountId;

    @org.springframework.data.annotation.Id
    private long consensusTimestamp;

    // Use a custom Writing/Reading converter if this is stored as a string or array in the DB
    @Builder.Default
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> effectivePayerAccountIds = Collections.emptyList();

    // Handled by global EntityIdConverter
    private EntityId tokenId;

    // Handled by global EntityIdConverter
    private EntityId payerAccountId;

    @JsonIgnore
    @Override
    public Id getId() {
        return new Id(collectorAccountId, consensusTimestamp);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        // True forces an INSERT; ideal for immutable transaction data
        return true;
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
