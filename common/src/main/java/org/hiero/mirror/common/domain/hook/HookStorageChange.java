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
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@Builder(toBuilder = true)
public class HookStorageChange implements Persistable<HookStorageChange.Id> {

    private boolean deleted;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    @JsonIgnore
    private Id id;

    @ToString.Exclude
    private byte[] valueRead;

    @ToString.Exclude
    private byte[] valueWritten;

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public void setConsensusTimestamp(long consensusTimestamp) {
        id().setConsensusTimestamp(consensusTimestamp);
    }

    public long getHookId() {
        return id != null ? id.getHookId() : 0L;
    }

    public void setHookId(long hookId) {
        id().setHookId(hookId);
    }

    public byte[] getKey() {
        return id != null ? id.getKey() : null;
    }

    public void setKey(byte[] key) {
        id().setKey(key);
    }

    public EntityId getOwnerId() {
        return id != null ? id.getOwnerId() : null;
    }

    public void setOwnerId(EntityId ownerId) {
        id().setOwnerId(ownerId);
    }

    public void setOwnerId(long ownerId) {
        setOwnerId(EntityId.of(ownerId));
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    public void setValueRead(byte[] valueRead) {
        this.valueRead = DomainUtils.trim(valueRead);
    }

    public void setValueWritten(byte[] valueWritten) {
        this.valueWritten = DomainUtils.trim(valueWritten);
        this.deleted = this.valueWritten != null && this.valueWritten.length == 0;
    }

    private Id id() {
        if (id == null) {
            id = new Id();
        }
        return id;
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -2847639184756392847L;

        private long consensusTimestamp;

        private long hookId;

        @ToString.Exclude
        private byte[] key;

        private EntityId ownerId;
    }

    public static class HookStorageChangeBuilder {

        private Id ensureId() {
            this.id = this.id == null
                    ? new Id()
                    : new Id(
                            this.id.getConsensusTimestamp(),
                            this.id.getHookId(),
                            this.id.getKey(),
                            this.id.getOwnerId());
            return this.id;
        }

        public HookStorageChangeBuilder consensusTimestamp(long consensusTimestamp) {
            ensureId().setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public HookStorageChangeBuilder hookId(long hookId) {
            ensureId().setHookId(hookId);
            return this;
        }

        public HookStorageChangeBuilder key(byte[] key) {
            ensureId().setKey(key);
            return this;
        }

        public HookStorageChangeBuilder ownerId(EntityId ownerId) {
            ensureId().setOwnerId(ownerId);
            return this;
        }

        public HookStorageChangeBuilder ownerId(long ownerId) {
            return ownerId(EntityId.of(ownerId));
        }

        public HookStorageChangeBuilder valueRead(byte[] valueRead) {
            this.valueRead = DomainUtils.trim(valueRead);
            return this;
        }

        public HookStorageChangeBuilder valueWritten(byte[] valueWritten) {
            this.valueWritten = DomainUtils.trim(valueWritten);
            this.deleted = this.valueWritten != null && this.valueWritten.length == 0;
            return this;
        }
    }
}
