// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;

@Data
@IdClass(AbstractTokenAirdrop.Id.class)
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public class AbstractTokenAirdrop implements History {

    private Long amount;

    @jakarta.persistence.Id
    private long receiverAccountId;

    @jakarta.persistence.Id
    private long senderAccountId;

    @jakarta.persistence.Id
    private long serialNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TokenAirdropStateEnum state;

    private Range<Long> timestampRange;

    @jakarta.persistence.Id
    private long tokenId;

    @JsonIgnore
    public Id getId() {
        Id id = new Id();
        id.setReceiverAccountId(receiverAccountId);
        id.setSenderAccountId(senderAccountId);
        id.setSerialNumber(serialNumber);
        id.setTokenId(tokenId);
        return id;
    }

    @Data
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -8165098238647325621L;

        private long receiverAccountId;
        private long senderAccountId;
        private long serialNumber;
        private long tokenId;
    }
}
