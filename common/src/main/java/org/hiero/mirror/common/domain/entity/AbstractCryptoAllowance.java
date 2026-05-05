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
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractCryptoAllowance implements FungibleAllowance, Persistable<AbstractCryptoAllowance.Id> {

    @UpsertColumn(coalesce = "case when {0} >= 0 then {0} else coalesce(e_{0}, 0) + coalesce({0}, 0) end")
    private long amount;

    private Long amountGranted;

    private EntityId payerAccountId;

    private Range<Long> timestampRange;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    @JsonIgnore
    @Override
    public Id getId() {
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
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

    /**
     *  Allows using
     *  CryptoAllowance.builder()
     *       .owner(entityId.getId())
     *       .spender(payerAccount.getId())
     *       ...
     *   instead of
     *   CryptoAllowance.builder()
     *       .id(new AbstractCryptoAllowance.Id(ownerValue, spenderValue))
     *       ...
     */
    public abstract static class AbstractCryptoAllowanceBuilder<
            C extends AbstractCryptoAllowance, B extends AbstractCryptoAllowanceBuilder<C, B>> {
        public B owner(long owner) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setOwner(owner);
            return self();
        }

        public B spender(long spender) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setSpender(spender);
            return self();
        }
    }
}
