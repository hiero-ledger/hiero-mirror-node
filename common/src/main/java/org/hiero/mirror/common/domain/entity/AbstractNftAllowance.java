// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
@SuperBuilder
@Upsertable(history = true)
public abstract class AbstractNftAllowance implements History {

    private boolean approvedForAll;

    @org.springframework.data.annotation.Id
    private long owner;

    // Converter removed. Handled by global EntityIdConverter bean.
    private EntityId payerAccountId;

    @org.springframework.data.annotation.Id
    private long spender;

    // JDBC: Requires custom Reading/Writing converters for PG 'int8range'
    private Range<Long> timestampRange;

    @org.springframework.data.annotation.Id
    private long tokenId;

    @JsonIgnore
    public AbstractNftAllowance.Id getId() {
        return new Id(owner, spender, tokenId);
    }

    @Data
    @NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = 4078820027811154183L;
        private long owner;
        private long spender;
        private long tokenId;
    }
}
