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
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractTokenAccount implements History, Persistable<AbstractTokenAccount.Id> {

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
    @Transient // Switched to Spring Data Transient
    private boolean claim;

    private Long createdTimestamp;

    // JDBC: Handled by global ordinal-to-enum converters
    @UpsertColumn(coalesce = """
            case when created_timestamp is not null then {0}
                 else coalesce({0}, e_{0})
            end
            """)
    private TokenFreezeStatusEnum freezeStatus;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    @UpsertColumn(coalesce = """
            case when created_timestamp is not null then {0}
                 else coalesce({0}, e_{0})
            end
            """)
    private TokenKycStatusEnum kycStatus;

    private Range<Long> timestampRange;

    public long getAccountId() {
        return id != null ? id.getAccountId() : 0L;
    }

    public void setAccountId(long accountId) {
        if (id == null) id = new Id();
        id.setAccountId(accountId);
    }

    public long getTokenId() {
        return id != null ? id.getTokenId() : 0L;
    }

    public void setTokenId(long tokenId) {
        if (id == null) id = new Id();
        id.setTokenId(tokenId);
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
    @lombok.AllArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = 4078820027811154183L;

        private long accountId;
        private long tokenId;
    }

    public abstract static class AbstractTokenAccountBuilder<
            C extends AbstractTokenAccount, B extends AbstractTokenAccountBuilder<C, B>> {
        public B accountId(long accountId) {
            if (this.id == null) this.id = new Id();
            this.id.setAccountId(accountId);
            return self();
        }

        public B tokenId(long tokenId) {
            if (this.id == null) this.id = new Id();
            this.id.setTokenId(tokenId);
            return self();
        }
    }
}
