// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@IdClass(AbstractHook.Id.class)
@Upsertable(history = true)
public abstract class AbstractHook implements History {

    private static final String UPSERTABLE_COLUMN_COALESCE =
            """
                    case when created_timestamp = lower(timestamp_range) then {0}
                         else coalesce({0}, e_{0})
                    end""";
    private static final String UPSERTABLE_COLUMN_WITH_DEFAULT_COALESCE =
            """
                    case when created_timestamp = lower(timestamp_range) then coalesce({0}, {1})
                         else coalesce({0}, e_{0}, {1})
                    end""";

    @ToString.Exclude
    @UpsertColumn(coalesce = UPSERTABLE_COLUMN_COALESCE)
    private byte[] adminKey;

    @UpsertColumn(coalesce = UPSERTABLE_COLUMN_COALESCE)
    private EntityId contractId;

    @UpsertColumn(coalesce = UPSERTABLE_COLUMN_COALESCE)
    private Long createdTimestamp;

    @UpsertColumn(coalesce = UPSERTABLE_COLUMN_WITH_DEFAULT_COALESCE)
    private Boolean deleted;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @UpsertColumn(coalesce = UPSERTABLE_COLUMN_WITH_DEFAULT_COALESCE)
    private HookExtensionPoint extensionPoint;

    @jakarta.persistence.Id
    private long hookId;

    @jakarta.persistence.Id
    private long ownerId;

    private Range<Long> timestampRange;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @UpsertColumn(coalesce = UPSERTABLE_COLUMN_WITH_DEFAULT_COALESCE)
    private HookType type;

    @JsonIgnore
    public Id getId() {
        return new Id(hookId, ownerId);
    }

    public void setOwnerId(EntityId ownerId) {
        this.ownerId = ownerId.getId();
    }

    public void setOwnerId(long ownerId) {
        this.ownerId = ownerId;
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -8745629837592847563L;

        private long hookId;
        private long ownerId;
    }
}
