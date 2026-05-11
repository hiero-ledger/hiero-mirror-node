// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractNft implements History, Persistable<AbstractNft.Id> {

    public static final long RETAIN_SPENDER = 0L;

    @UpsertColumn(coalesce = "case when deleted = true then null else coalesce({0}, e_{0}, {1}) end")
    private EntityId accountId;

    private Long createdTimestamp;

    @UpsertColumn(coalesce = "case when {0} is not null and {0} = 0 then e_{0} else {0} end")
    private Long delegatingSpender;

    private Boolean deleted;

    @ToString.Exclude
    private byte[] metadata;

    @UpsertColumn(coalesce = "case when {0} is not null and {0} = 0 then e_{0} else {0} end")
    private Long spender;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private Range<Long> timestampRange;

    public long getSerialNumber() {
        return id != null ? id.getSerialNumber() : 0L;
    }

    public long getTokenId() {
        return id != null ? id.getTokenId() : 0L;
    }

    public void setSerialNumber(long serialNumber) {
        initId();
        id.setSerialNumber(serialNumber);
    }

    public void setTokenId(long tokenId) {
        initId();
        id.setTokenId(tokenId);
    }

    private void initId() {
        if (id == null) {
            id = new Id();
        }
    }

    public static boolean shouldKeepSpender(Long spender) {
        return spender != null && spender == RETAIN_SPENDER;
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

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 8679156797431231527L;

        private long serialNumber;
        private long tokenId;
    }

    public abstract static class AbstractNftBuilder<C extends AbstractNft, B extends AbstractNftBuilder<C, B>> {

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

        private void initId() {
            if (this.id == null) {
                this.id = new Id();
            }
        }
    }
}
