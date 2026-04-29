// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("contract_transaction")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractTransaction implements Persistable<ContractTransaction.Id> {

    @org.springframework.data.annotation.Id
    private Long consensusTimestamp;

    @org.springframework.data.annotation.Id
    private Long entityId;

    // JDBC: Requires a custom Reading/Writing converter (e.g., to PG array or CSV)
    // to prevent Spring Data JDBC from treating this as a One-to-Many relationship.
    @Builder.Default
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> contractIds = Collections.emptyList();

    private long payerAccountId;

    @Override
    @JsonIgnore
    public Id getId() {
        return new ContractTransaction.Id(consensusTimestamp, entityId);
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return true; // Optimizes ingestion by forcing INSERT without checking existence
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -6807023295883699004L;

        private long consensusTimestamp;
        private long entityId;
    }
}
