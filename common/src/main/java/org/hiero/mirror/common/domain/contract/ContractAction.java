// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@IdClass(ContractAction.Id.class)
@NoArgsConstructor
public class ContractAction implements Persistable<ContractAction.Id> {

    private int callDepth;

    private EntityId caller;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EntityType callerType;

    private int callOperationType;

    private Integer callType;

    @jakarta.persistence.Id
    private long consensusTimestamp;

    private long gas;

    private long gasUsed;

    @jakarta.persistence.Id
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
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @JsonIgnore
    public boolean hasRevertReason() {
        return resultDataType == REVERT_REASON.getNumber();
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
