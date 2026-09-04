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
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractNftAllowance implements History {

    private boolean approvedForAll;

    private EntityId payerAccountId;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    @JsonIgnore
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
        id().setOwner(owner);
    }

    public void setSpender(long spender) {
        id().setSpender(spender);
    }

    public void setTokenId(long tokenId) {
        id().setTokenId(tokenId);
    }

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

        private long tokenId;
    }

    public abstract static class AbstractNftAllowanceBuilder<
            C extends AbstractNftAllowance, B extends AbstractNftAllowanceBuilder<C, B>> {

        private Id ensureId() {
            if (this.id == null) {
                this.id = new Id();
            }
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

        public B tokenId(long tokenId) {
            ensureId().setTokenId(tokenId);
            return self();
        }
    }
}
