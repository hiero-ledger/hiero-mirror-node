// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;

@Data
@IdClass(AbstractNftAllowance.Id.class)
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder
@Upsertable(history = true)
public abstract class AbstractNftAllowance implements History {

    private boolean approvedForAll;

    @jakarta.persistence.Id
    private long owner;

    private EntityId payerAccountId;

    @jakarta.persistence.Id
    private long spender;

    private Range<Long> timestampRange;

    @jakarta.persistence.Id
    private long tokenId;

    @JsonIgnore
    public AbstractNftAllowance.Id getId() {
        Id id = new Id();
        id.setOwner(owner);
        id.setSpender(spender);
        id.setTokenId(tokenId);
        return id;
    }

    @Data
    public static class Id implements Serializable {

        private static final long serialVersionUID = 4078820027811154183L;

        private long owner;
        private long spender;
        private long tokenId;
    }
}
