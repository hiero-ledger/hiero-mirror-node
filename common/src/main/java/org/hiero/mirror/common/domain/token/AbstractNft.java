// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
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
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;

@Data
@IdClass(AbstractNft.Id.class)
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractNft implements History {

    // sentinel value to indicate delegating spender / spender should keep its previous value
    public static final long RETAIN_SPENDER = 0L;

    @UpsertColumn(coalesce = "case when deleted = true then null else coalesce({0}, e_{0}, {1}) end")
    private EntityId accountId;

    @Column(updatable = false)
    private Long createdTimestamp;

    @UpsertColumn(coalesce = "case when {0} is not null and {0} = 0 then e_{0} else {0} end")
    private Long delegatingSpender;

    private Boolean deleted;

    @ToString.Exclude
    private byte[] metadata;

    @jakarta.persistence.Id
    private long serialNumber;

    @UpsertColumn(coalesce = "case when {0} is not null and {0} = 0 then e_{0} else {0} end")
    private Long spender;

    @jakarta.persistence.Id
    private long tokenId;

    private Range<Long> timestampRange;

    @JsonIgnore
    public AbstractNft.Id getId() {
        return new Id(serialNumber, tokenId);
    }

    public static boolean shouldKeepSpender(Long spender) {
        return spender != null && spender == RETAIN_SPENDER;
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
