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
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractTokenAirdrop implements History {

    private Long amount;

    private TokenAirdropStateEnum state;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    @JsonIgnore
    private Id id;

    private Range<Long> timestampRange;

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

    public void setReceiverAccountId(long receiverAccountId) {
        if (id == null) {
            id = new Id();
        }
        id.setReceiverAccountId(receiverAccountId);
    }

    public void setSenderAccountId(long senderAccountId) {
        if (id == null) {
            id = new Id();
        }
        id.setSenderAccountId(senderAccountId);
    }

    public void setSerialNumber(long serialNumber) {
        if (id == null) {
            id = new Id();
        }
        id.setSerialNumber(serialNumber);
    }

    public void setTokenId(long tokenId) {
        if (id == null) {
            id = new Id();
        }
        id.setTokenId(tokenId);
    }

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

    public abstract static class AbstractTokenAirdropBuilder<
            C extends AbstractTokenAirdrop, B extends AbstractTokenAirdropBuilder<C, B>> {

        public B receiverAccountId(long receiverAccountId) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setReceiverAccountId(receiverAccountId);
            return self();
        }

        public B senderAccountId(long senderAccountId) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setSenderAccountId(senderAccountId);
            return self();
        }

        public B serialNumber(long serialNumber) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setSerialNumber(serialNumber);
            return self();
        }

        public B tokenId(long tokenId) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setTokenId(tokenId);
            return self();
        }
    }
}
