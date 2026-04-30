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
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("hook_storage_change")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@Builder(toBuilder = true)
public class HookStorageChange implements Persistable<HookStorageChange.Id> {

    private boolean deleted;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    @ToString.Exclude
    private byte[] valueRead;

    @ToString.Exclude
    private byte[] valueWritten;

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public void setConsensusTimestamp(long consensusTimestamp) {
        if (id == null) {
            id = new Id();
        }
        id.setConsensusTimestamp(consensusTimestamp);
    }

    public long getHookId() {
        return id != null ? id.getHookId() : 0L;
    }

    public void setHookId(long hookId) {
        if (id == null) {
            id = new Id();
        }
        id.setHookId(hookId);
    }

    public byte[] getKey() {
        return id != null ? id.getKey() : null;
    }

    public void setKey(byte[] key) {
        if (id == null) {
            id = new Id();
        }
        id.setKey(key);
    }

    public long getOwnerId() {
        return id != null ? id.getOwnerId() : 0L;
    }

    public void setOwnerId(long ownerId) {
        if (id == null) {
            id = new Id();
        }
        id.setOwnerId(ownerId);
    }

    @Override
    @JsonIgnore
    public Id getId() {
        return id;
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

        private long ownerId;
    }

    public static class HookStorageChangeBuilder {
        public HookStorageChangeBuilder consensusTimestamp(long consensusTimestamp) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public HookStorageChangeBuilder hookId(long hookId) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setHookId(hookId);
            return this;
        }

        public HookStorageChangeBuilder key(byte[] key) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setKey(key);
            return this;
        }

        public HookStorageChangeBuilder ownerId(long ownerId) {
            if (this.id == null) {
                this.id = new Id();
            }
            this.id.setOwnerId(ownerId);
            return this;
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
