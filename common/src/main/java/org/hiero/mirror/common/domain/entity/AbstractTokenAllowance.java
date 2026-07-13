// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
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

    private Range<Long> timestampRange;

    public long getOwner() {
        return id != null ? id.getOwner() : 0L;
    }

    public long getSpender() {
        return id != null ? id.getSpender() : 0L;
    }

    public long getTokenId() {
        return id != null ? id.getTokenId() : 0L;
    }

    public void setOwner(long owner) {
        id = (id == null ? new Id() : id).withOwner(owner);
    }

    public void setSpender(long spender) {
        id = (id == null ? new Id() : id).withSpender(spender);
    }

    public void setTokenId(long tokenId) {
        id = (id == null ? new Id() : id).withTokenId(tokenId);
    }

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
    @With
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 4078820027811154183L;

        private long owner;
        private long spender;
        private long tokenId;
    }

    public abstract static class AbstractTokenAllowanceBuilder<
            C extends AbstractTokenAllowance, B extends AbstractTokenAllowanceBuilder<C, B>> {

        public B owner(long owner) {
            this.id = (this.id == null ? new Id() : this.id).withOwner(owner);
            return self();
        }

        public B spender(long spender) {
            this.id = (this.id == null ? new Id() : this.id).withSpender(spender);
            return self();
        }

        public B tokenId(long tokenId) {
            this.id = (this.id == null ? new Id() : this.id).withTokenId(tokenId);
            return self();
        }
    }
}
