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
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Data
@NoArgsConstructor
@Table
public class HookStorageChange implements Persistable<HookStorageChange.Id> {

    @Transient
    private long consensusTimestamp;

    private boolean deleted;

    @Transient
    private long hookId;

    @Transient
    @ToString.Exclude
    private byte[] key;

    @Transient
    private EntityId ownerId;

    @ToString.Exclude
    private byte[] valueRead;

    @ToString.Exclude
    private byte[] valueWritten;

    @Builder(toBuilder = true)
    private HookStorageChange(
            long consensusTimestamp, long hookId, byte[] key, EntityId ownerId, byte[] valueRead, byte[] valueWritten) {
        this.consensusTimestamp = consensusTimestamp;
        this.hookId = hookId;
        this.key = key;
        this.ownerId = ownerId;
        this.valueRead = DomainUtils.trim(valueRead);
        this.valueWritten = DomainUtils.trim(valueWritten);
        this.deleted = this.valueWritten != null && this.valueWritten.length == 0;
    }

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @Override
    @JsonIgnore
    public Id getId() {
        Id id = new Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setHookId(hookId);
        id.setKey(key);
        id.setOwnerId(ownerId);
        return id;
    }

    public void setId(Id id) {
        if (id == null) {
            return;
        }

        this.consensusTimestamp = id.getConsensusTimestamp();
        this.hookId = id.getHookId();
        this.key = id.getKey();
        this.ownerId = id.getOwnerId();
    }

    public void setValueRead(byte[] valueRead) {
        this.valueRead = DomainUtils.trim(valueRead);
    }

    public void setValueWritten(byte[] valueWritten) {
        this.valueWritten = DomainUtils.trim(valueWritten);
        this.deleted = this.valueWritten != null && this.valueWritten.length == 0;
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
        private static final long serialVersionUID = -2847639184756392847L;

        private long consensusTimestamp;
        private long hookId;
        private byte[] key;
        private EntityId ownerId;
    }

    public static class HookStorageChangeBuilder {
        public HookStorageChangeBuilder ownerId(EntityId ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public HookStorageChangeBuilder ownerId(long ownerId) {
            return ownerId(EntityId.of(ownerId));
        }
    }
}
