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
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractTokenAllowance implements FungibleAllowance, Persistable<AbstractTokenAllowance.Id> {

    @UpsertColumn(coalesce = "case when {0} >= 0 then {0} else coalesce(e_{0}, 0) + coalesce({0}, 0) end")
    private long amount;

    private Long amountGranted;

    private EntityId payerAccountId;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    // JDBC: Marked as @Transient to avoid AOT/Reflection errors into Guava internals.
    // Your custom converters will still handle this field at the JDBC level.
    @Transient
    private Range<Long> timestampRange;

    // --- Convenience Accessors ---

    public long getOwner() {
        return id != null ? id.getOwner() : 0L;
    }

    public long getSpender() {
        return id != null ? id.getSpender() : 0L;
    }

    public long getTokenId() {
        return id != null ? id.getTokenId() : 0L;
    }

    // --- Persistable Implementation ---

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

    // --- Composite ID Class ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 4078820027811154183L;

        private long owner;
        private long spender;
        private long tokenId;
    }

    // --- Custom Builder Bridge ---
    // This allows .owner(), .spender(), and .tokenId() to work on the SuperBuilder
    public abstract static class AbstractTokenAllowanceBuilder<
            C extends AbstractTokenAllowance, B extends AbstractTokenAllowanceBuilder<C, B>> {

        public B owner(long owner) {
            initId();
            this.id.setOwner(owner);
            return self();
        }

        public B spender(long spender) {
            initId();
            this.id.setSpender(spender);
            return self();
        }

        public B tokenId(long tokenId) {
            initId();
            this.id.setTokenId(tokenId);
            return self();
        }

        private void initId() {
            if (this.id == null) {
                this.id = new Id();
            }
        }
    }
}
