// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder
@Upsertable(history = true)
public abstract class AbstractNftAllowance implements History {

    private boolean approvedForAll;

    @Transient
    private long owner;

    private EntityId payerAccountId;

    @Transient
    private long spender;

    private Range<Long> timestampRange;

    @Transient
    private long tokenId;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    public AbstractNftAllowance.Id getId() {
        Id id = new Id();
        id.setOwner(owner);
        id.setSpender(spender);
        id.setTokenId(tokenId);
        return id;
    }

    public void setId(AbstractNftAllowance.Id id) {
        if (id == null) {
            return;
        }

        this.owner = id.getOwner();
        this.spender = id.getSpender();
        this.tokenId = id.getTokenId();
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = 4078820027811154183L;

        private long owner;
        private long spender;
        private long tokenId;
    }
}
