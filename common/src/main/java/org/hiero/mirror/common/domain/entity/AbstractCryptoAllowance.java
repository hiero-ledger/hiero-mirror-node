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
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractCryptoAllowance implements FungibleAllowance {

    @UpsertColumn(coalesce = "case when {0} >= 0 then {0} else coalesce(e_{0}, 0) + coalesce({0}, 0) end")
    private long amount;

    private Long amountGranted;

    private EntityId payerAccountId;

    private Range<Long> timestampRange;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    @JsonIgnore
    private Id id;

    private Id id() {
        if (id == null) {
            id = new Id();
        }
        return id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 4078820027811154183L;

        private long owner;

        private long spender;
    }

    public long getOwner() {
        return id != null ? id.getOwner() : 0L;
    }

    public long getSpender() {
        return id != null ? id.getSpender() : 0L;
    }

    public void setOwner(long owner) {
        id().setOwner(owner);
    }

    public void setSpender(long spender) {
        id().setSpender(spender);
    }

    public abstract static class AbstractCryptoAllowanceBuilder<
            C extends AbstractCryptoAllowance, B extends AbstractCryptoAllowanceBuilder<C, B>> {

        private Id ensureId() {
            this.id = this.id == null ? new Id() : new Id(this.id.getOwner(), this.id.getSpender());
            return this.id;
        }

        public B owner(long owner) {
            ensureId().setOwner(owner);
            return self();
        }

        public B spender(long spender) {
            ensureId().setSpender(spender);
            return self();
        }
    }
}
