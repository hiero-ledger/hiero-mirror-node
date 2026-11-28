// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractTokenAirdrop implements History {

    // A negative amount is a change to add to the existing pending amount
    @UpsertColumn(coalesce = """
            case when {0} >= 0 then {0}
                 when {0} < 0 then case when e_state = ''PENDING'' then coalesce(e_{0}, 0) else 0 end - {0}
                 else e_{0}
            end
            """)
    private Long amount;

    @Transient
    private long receiverAccountId;

    @Transient
    private long senderAccountId;

    @Transient
    private long serialNumber;

    private TokenAirdropStateEnum state;

    private Range<Long> timestampRange;

    @Transient
    private long tokenId;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    public Id getId() {
        Id id = new Id();
        id.setReceiverAccountId(receiverAccountId);
        id.setSenderAccountId(senderAccountId);
        id.setSerialNumber(serialNumber);
        id.setTokenId(tokenId);
        return id;
    }

    public void setId(Id id) {
        if (id == null) {
            return;
        }

        this.receiverAccountId = id.getReceiverAccountId();
        this.senderAccountId = id.getSenderAccountId();
        this.serialNumber = id.getSerialNumber();
        this.tokenId = id.getTokenId();
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -8165098238647325621L;

        private long receiverAccountId;
        private long senderAccountId;
        private long serialNumber;
        private long tokenId;
    }
}
