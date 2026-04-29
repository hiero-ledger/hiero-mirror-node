// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

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
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractTokenAirdrop implements History {

    private Long amount;

    @org.springframework.data.annotation.Id
    private long receiverAccountId;

    @org.springframework.data.annotation.Id
    private long senderAccountId;

    @org.springframework.data.annotation.Id
    private long serialNumber;

    // Handled by global Reading/Writing converters for Postgres Named Enum
    private TokenAirdropStateEnum state;

    // JDBC: Requires custom Reading/Writing converters for PG 'int8range'
    private Range<Long> timestampRange;

    @org.springframework.data.annotation.Id
    private long tokenId;

    @JsonIgnore
    public Id getId() {
        return new Id(receiverAccountId, senderAccountId, serialNumber, tokenId);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -8165098238647325621L;

        private long receiverAccountId;
        private long senderAccountId;
        private long serialNumber;
        private long tokenId;
    }
}
