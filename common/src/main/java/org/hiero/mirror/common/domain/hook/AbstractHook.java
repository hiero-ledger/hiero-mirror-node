// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

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
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Embedded;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractHook implements History {

    private static final String UPSERTABLE_COLUMN_COALESCE = """
                    case when created_timestamp = lower(timestamp_range) then {0}
                         else coalesce({0}, e_{0})
                    end""";
    private static final String UPSERTABLE_COLUMN_WITH_DEFAULT_COALESCE = """
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

    @UpsertColumn(coalesce = UPSERTABLE_COLUMN_WITH_DEFAULT_COALESCE)
    private HookExtensionPoint extensionPoint;

    @Transient
    private long hookId;

    @Transient
    private EntityId ownerId;

    private Range<Long> timestampRange;

    @UpsertColumn(coalesce = UPSERTABLE_COLUMN_WITH_DEFAULT_COALESCE)
    private HookType type;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    public Id getId() {
        return new Id(hookId, ownerId);
    }

    public void setId(Id id) {
        if (id == null) {
            return;
        }

        this.hookId = id.getHookId();
        this.ownerId = id.getOwnerId();
    }

    public void setOwnerId(EntityId ownerId) {
        this.ownerId = ownerId;
    }

    public void setOwnerId(long ownerId) {
        this.ownerId = EntityId.of(ownerId);
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -8745629837592847563L;

        private long hookId;
        private EntityId ownerId;

        public Id(long hookId, long ownerId) {
            this(hookId, EntityId.of(ownerId));
        }
    }

    public abstract static class AbstractHookBuilder<C extends AbstractHook, B extends AbstractHookBuilder<C, B>> {

        public B ownerId(EntityId ownerId) {
            this.ownerId = ownerId;
            return self();
        }

        public B ownerId(long ownerId) {
            return ownerId(EntityId.of(ownerId));
        }
    }
}
