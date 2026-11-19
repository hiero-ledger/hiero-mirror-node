// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractCryptoAllowance implements FungibleAllowance {

    @UpsertColumn(coalesce = "case when {0} >= 0 then {0} else coalesce(e_{0}, 0) + coalesce({0}, 0) end")
    private long amount;

    private Long amountGranted;

    @Transient
    private long owner;

    private EntityId payerAccountId;

    @Transient
    private long spender;

    private Range<Long> timestampRange;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    public Id getId() {
        Id id = new Id();
        id.setOwner(owner);
        id.setSpender(spender);
        return id;
    }

    public void setId(Id id) {
        if (id == null) {
            return;
        }

        this.owner = id.getOwner();
        this.spender = id.getSpender();
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 4078820027811154183L;

        private long owner;
        private long spender;
    }
}
