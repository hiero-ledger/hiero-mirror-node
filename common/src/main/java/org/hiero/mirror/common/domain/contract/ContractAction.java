// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.With;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Table
@NoArgsConstructor
public class ContractAction implements Persistable<ContractAction.Id> {

    private int callDepth;

    private EntityId caller;

    private EntityType callerType;

    private int callOperationType;

    private Integer callType;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private long gas;

    private long gasUsed;

    @ToString.Exclude
    private byte[] input;

    private EntityId payerAccountId;

    private EntityId recipientAccount;

    @ToString.Exclude
    private byte[] recipientAddress;

    private EntityId recipientContract;

    @ToString.Exclude
    private byte[] resultData;

    private int resultDataType;

    private long value;

    public void setConsensusTimestamp(long consensusTimestamp) {
        id = (id == null ? new Id() : id).withConsensusTimestamp(consensusTimestamp);
    }

    public void setIndex(int index) {
        id = (id == null ? new Id() : id).withIndex(index);
    }

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public int getIndex() {
        return id != null ? id.getIndex() : 0;
    }

    @Override
    @JsonIgnore
    public ContractAction.Id getId() {
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Spring Data JDBC querying before insert
    }

    @JsonIgnore
    public boolean hasRevertReason() {
        return resultDataType == REVERT_REASON.getNumber();
    }

    @Data
    @AllArgsConstructor
    @Builder
    @NoArgsConstructor
    @With
    public static class Id implements Serializable {
        private static final long serialVersionUID = -6192177810161178246L;

        private long consensusTimestamp;

        private int index;
    }
}
