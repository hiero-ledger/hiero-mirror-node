// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.UpsertColumn;
import com.hedera.mirror.common.domain.Upsertable;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@IdClass(AbstractCryptoAllowance.Id.class)
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractCryptoAllowance implements FungibleAllowance {

    @UpsertColumn(coalesce = "case when {0} >= 0 then {0} else coalesce(e_{0}, 0) + coalesce({0}, 0) end")
    private long amount;

    private Long amountGranted;

    @jakarta.persistence.Id
    private long owner;

    private EntityId payerAccountId;

    @jakarta.persistence.Id
    private long spender;

    private Range<Long> timestampRange;

    @JsonIgnore
    public Id getId() {
        Id id = new Id();
        id.setOwner(owner);
        id.setSpender(spender);
        return id;
    }

    @Data
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 4078820027811154183L;

        private long owner;
        private long spender;
    }
}
