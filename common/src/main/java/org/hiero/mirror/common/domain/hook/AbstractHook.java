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

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    // JDBC: writing/reading via RangeToPGobjectWritingConverter / PGobjectToRangeReadingConverter
    @Transient
    private Range<Long> timestampRange;

    @UpsertColumn(coalesce = UPSERTABLE_COLUMN_WITH_DEFAULT_COALESCE)
    private HookType type;

    public long getHookId() {
        return id != null ? id.getHookId() : 0L;
    }

    public void setHookId(long hookId) {
        if (id == null) {
            id = new Id();
        }
        id.setHookId(hookId);
    }

    public long getOwnerId() {
        return id != null ? id.getOwnerId() : 0L;
    }

    public void setOwnerId(EntityId ownerId) {
        setOwnerId(ownerId.getId());
    }

    public void setOwnerId(long ownerId) {
        if (id == null) {
            id = new Id();
        }
        id.setOwnerId(ownerId);
    }

    @JsonIgnore
    public Id getId() {
        return id;
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

    @SuppressWarnings("java:S1610")
    public abstract static class AbstractHookBuilder<C extends AbstractHook, B extends AbstractHookBuilder<C, B>> {
        public B hookId(long hookId) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setHookId(hookId);
            return self();
        }

        public B ownerId(long ownerId) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setOwnerId(ownerId);
            return self();
        }

        public B ownerId(EntityId ownerId) {
            return ownerId(ownerId.getId());
        }
    }
}
