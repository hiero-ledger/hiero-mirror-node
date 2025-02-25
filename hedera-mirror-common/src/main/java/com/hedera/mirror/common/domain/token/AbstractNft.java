// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.common.domain.UpsertColumn;
import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Column;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@IdClass(AbstractNft.Id.class)
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractNft implements History {

    @UpsertColumn(coalesce = "case when deleted = true then null else coalesce({0}, e_{0}, {1}) end")
    private EntityId accountId;

    @Column(updatable = false)
    private Long createdTimestamp;

    @UpsertColumn(coalesce = "case when delegating_spender < 0 then e_{0} else {0} end")
    private EntityId delegatingSpender;

    private Boolean deleted;

    @ToString.Exclude
    private byte[] metadata;

    @jakarta.persistence.Id
    private long serialNumber;

    @UpsertColumn(coalesce = "case when spender < 0 then e_{0} else {0} end")
    private EntityId spender;

    @jakarta.persistence.Id
    private long tokenId;

    private Range<Long> timestampRange;

    @JsonIgnore
    public AbstractNft.Id getId() {
        return new Id(serialNumber, tokenId);
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
}
