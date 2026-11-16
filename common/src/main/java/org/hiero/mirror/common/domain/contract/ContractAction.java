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
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@NoArgsConstructor
@Table
public class ContractAction implements Persistable<ContractAction.Id> {

    private int callDepth;

    private EntityId caller;

    private EntityType callerType;

    private int callOperationType;

    private Integer callType;

    @Transient
    private long consensusTimestamp;

    private long gas;

    private long gasUsed;

    @Transient
    private int index;

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

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @Override
    @JsonIgnore
    public ContractAction.Id getId() {
        ContractAction.Id id = new ContractAction.Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setIndex(index);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid querying before insert
    }

    @JsonIgnore
    public boolean hasRevertReason() {
        return resultDataType == REVERT_REASON.getNumber();
    }

    public void setId(ContractAction.Id id) {
        if (id == null) {
            return;
        }

        this.consensusTimestamp = id.getConsensusTimestamp();
        this.index = id.getIndex();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = -6192177810161178246L;
        private long consensusTimestamp;
        private int index;
    }
}
