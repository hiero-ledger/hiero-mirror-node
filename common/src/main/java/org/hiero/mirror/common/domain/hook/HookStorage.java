// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@Table
@Upsertable
public class HookStorage {
    private static final String CREATED_TS_COALESCE = """
                    case when coalesce(e_deleted, true) then abs(created_timestamp)
                         when created_timestamp < 0 then abs(created_timestamp)
                         else e_created_timestamp
                    end
                    """;

    private static final int KEY_BYTE_LENGTH = 32;

    @UpsertColumn(coalesce = CREATED_TS_COALESCE)
    private long createdTimestamp;

    @Transient
    private long hookId;

    private boolean deleted;

    @Transient
    @ToString.Exclude
    private byte[] key;

    private Long modifiedTimestamp;

    @Transient
    private EntityId ownerId;

    @ToString.Exclude
    private byte[] value;

    @Builder(toBuilder = true)
    private HookStorage(
            long createdTimestamp, long hookId, byte[] key, Long modifiedTimestamp, EntityId ownerId, byte[] value) {
        this.createdTimestamp = createdTimestamp;
        this.hookId = hookId;
        this.key = DomainUtils.leftPadBytes(key, KEY_BYTE_LENGTH);
        this.modifiedTimestamp = modifiedTimestamp;
        this.ownerId = ownerId;
        this.value = DomainUtils.trim(value);
        this.deleted = ArrayUtils.isEmpty(this.value);
    }

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    public HookStorage.Id getId() {
        HookStorage.Id id = new HookStorage.Id();
        id.setHookId(hookId);
        id.setKey(key);
        id.setOwnerId(ownerId);
        return id;
    }

    public void setId(HookStorage.Id id) {
        if (id == null) {
            return;
        }

        this.hookId = id.getHookId();
        this.key = id.getKey();
        this.ownerId = id.getOwnerId();
    }

    public void setKey(byte[] key) {
        this.key = DomainUtils.leftPadBytes(key, KEY_BYTE_LENGTH);
    }

    public void setValue(byte[] value) {
        this.value = DomainUtils.trim(value);
        this.deleted = ArrayUtils.isEmpty(this.value);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = 4567832945612847391L;

        private long hookId;
        private byte[] key;
        private EntityId ownerId;
    }

    public static class HookStorageBuilder {
        public HookStorageBuilder ownerId(EntityId ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public HookStorageBuilder ownerId(long ownerId) {
            return ownerId(EntityId.of(ownerId));
        }
    }
}
