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
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractTokenAirdrop implements History, Persistable<AbstractTokenAirdrop.Id> {

    private Long amount;

    /** Stored as Postgres {@code airdrop_state}; see {@link PostgresAirdropState}. */
    @Column("state")
    private PostgresAirdropState airdropState;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    // JDBC: Persisted via RangeToPGobjectWritingConverter / PGobjectToRangeReadingConverter
    private Range<Long> timestampRange;

    public TokenAirdropStateEnum getState() {
        return airdropState == null ? null : airdropState.getState();
    }

    public void setState(TokenAirdropStateEnum state) {
        airdropState = PostgresAirdropState.of(state);
    }

    // --- Convenience Accessors ---

    public long getReceiverAccountId() {
        return id != null ? id.getReceiverAccountId() : 0L;
    }

    public long getSenderAccountId() {
        return id != null ? id.getSenderAccountId() : 0L;
    }

    public long getSerialNumber() {
        return id != null ? id.getSerialNumber() : 0L;
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
        private static final long serialVersionUID = -8165098238647325621L;

        private long receiverAccountId;
        private long senderAccountId;
        private long serialNumber;
        private long tokenId;
    }

    // --- Custom SuperBuilder Bridge ---
    public abstract static class AbstractTokenAirdropBuilder<
            C extends AbstractTokenAirdrop, B extends AbstractTokenAirdropBuilder<C, B>> {

        public B receiverAccountId(long receiverAccountId) {
            initId();
            this.id.setReceiverAccountId(receiverAccountId);
            return self();
        }

        public B senderAccountId(long senderAccountId) {
            initId();
            this.id.setSenderAccountId(senderAccountId);
            return self();
        }

        public B serialNumber(long serialNumber) {
            initId();
            this.id.setSerialNumber(serialNumber);
            return self();
        }

        public B tokenId(long tokenId) {
            initId();
            this.id.setTokenId(tokenId);
            return self();
        }

        public B state(TokenAirdropStateEnum state) {
            this.airdropState = PostgresAirdropState.of(state);
            return self();
        }

        private void initId() {
            if (this.id == null) {
                this.id = new Id();
            }
        }
    }
}
