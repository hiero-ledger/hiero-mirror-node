// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractTokenAccount implements History {

    @Transient
    private long accountId;

    private Boolean associated;

    private Boolean automaticAssociation;

    @UpsertColumn(coalesce = """
            case when created_timestamp is not null then {0}
                 else coalesce(e_{0}, 0) + coalesce({0}, 0)
            end
            """)
    private long balance;

    private Long balanceTimestamp;

    @JsonIgnore
    @SuppressWarnings("java:S2065")
    @Transient
    private transient boolean claim;

    private Long createdTimestamp;

    @UpsertColumn(coalesce = """
            case when created_timestamp is not null then {0}
                 else coalesce({0}, e_{0})
            end
            """)
    private TokenFreezeStatusEnum freezeStatus;

    @UpsertColumn(coalesce = """
            case when created_timestamp is not null then {0}
                 else coalesce({0}, e_{0})
            end
            """)
    private TokenKycStatusEnum kycStatus;

    private Range<Long> timestampRange;

    @Transient
    private long tokenId;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    public AbstractTokenAccount.Id getId() {
        Id id = new AbstractTokenAccount.Id();
        id.setAccountId(accountId);
        id.setTokenId(tokenId);
        return id;
    }

    public void setId(AbstractTokenAccount.Id id) {
        if (id == null) {
            return;
        }

        this.accountId = id.getAccountId();
        this.tokenId = id.getTokenId();
    }

    @Data
    @NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = 4078820027811154183L;

        private long accountId;
        private long tokenId;
    }
}
