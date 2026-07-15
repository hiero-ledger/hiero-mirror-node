// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.With;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("hook_storage")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@Builder(toBuilder = true)
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

    private boolean deleted;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private Long modifiedTimestamp;

    @ToString.Exclude
    private byte[] value;

    public long getHookId() {
        return id != null ? id.getHookId() : 0L;
    }

    public void setHookId(long hookId) {
        id = (id == null ? new Id() : id).withHookId(hookId);
    }

    public byte[] getKey() {
        return id != null ? id.getKey() : null;
    }

    public void setKey(byte[] key) {
        id = (id == null ? new Id() : id).withKey(DomainUtils.leftPadBytes(key, KEY_BYTE_LENGTH));
    }

    public long getOwnerId() {
        return id != null ? id.getOwnerId() : 0L;
    }

    public void setOwnerId(long ownerId) {
        id = (id == null ? new Id() : id).withOwnerId(ownerId);
    }

    @JsonIgnore
    public Id getId() {
        return id;
    }

    public void setValue(byte[] value) {
        this.value = DomainUtils.trim(value);
        this.deleted = ArrayUtils.isEmpty(this.value);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @With
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = 4567832945612847391L;

        private long hookId;

        @ToString.Exclude
        private byte[] key;

        private long ownerId;
    }

    public static class HookStorageBuilder {
        public HookStorageBuilder hookId(long hookId) {
            this.id = (this.id == null ? new Id() : this.id).withHookId(hookId);
            return this;
        }

        public HookStorageBuilder key(byte[] key) {
            this.id = (this.id == null ? new Id() : this.id).withKey(DomainUtils.leftPadBytes(key, KEY_BYTE_LENGTH));
            return this;
        }

        public HookStorageBuilder ownerId(long ownerId) {
            this.id = (this.id == null ? new Id() : this.id).withOwnerId(ownerId);
            return this;
        }

        public HookStorageBuilder value(byte[] value) {
            this.value = DomainUtils.trim(value);
            this.deleted = ArrayUtils.isEmpty(this.value);
            return this;
        }
    }
}
