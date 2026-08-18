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
import lombok.With;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ContractTransaction implements Persistable<ContractTransaction.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    @Builder.Default
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> contractIds = Collections.emptyList();

    private long payerAccountId;

    // Replaces rather than mutates the builder's Id so objects built earlier from the same (reused) builder keep
    // their values, matching the snapshot semantics the flat JPA fields had
    public static class ContractTransactionBuilder {
        public ContractTransactionBuilder consensusTimestamp(long consensusTimestamp) {
            this.id = (this.id == null ? new Id() : this.id).withConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public ContractTransactionBuilder entityId(long entityId) {
            this.id = (this.id == null ? new Id() : this.id).withEntityId(entityId);
            return this;
        }
    }

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public long getEntityId() {
        return id != null ? id.getEntityId() : 0L;
    }

    @Override
    @JsonIgnore
    public Id getId() {
        return id;
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @With
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -6807023295883699004L;

        private long consensusTimestamp;

        private long entityId;
    }
}
