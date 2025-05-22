// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.springframework.data.domain.Persistable;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ContractTransaction.Id.class)
public class ContractTransaction implements Persistable<ContractTransaction.Id> {
    @jakarta.persistence.Id
    private Long consensusTimestamp;

    @jakarta.persistence.Id
    private Long entityId;

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
        return true;
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
