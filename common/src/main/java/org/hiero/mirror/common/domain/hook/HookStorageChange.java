// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Data
@Entity
@IdClass(HookStorageChange.Id.class)
@NoArgsConstructor
public class HookStorageChange implements Persistable<HookStorageChange.Id> {

    @jakarta.persistence.Id
    private long consensusTimestamp;

    private boolean deleted;

    @jakarta.persistence.Id
    private long hookId;

    @jakarta.persistence.Id
    @ToString.Exclude
    private byte[] key;

    @jakarta.persistence.Id
    private long ownerId;

    @ToString.Exclude
    private byte[] valueRead;

    @ToString.Exclude
    private byte[] valueWritten;

    @Builder(toBuilder = true)
    private HookStorageChange(
            long consensusTimestamp, long hookId, byte[] key, long ownerId, byte[] valueRead, byte[] valueWritten) {
        this.consensusTimestamp = consensusTimestamp;
        this.hookId = hookId;
        this.key = key;
        this.ownerId = ownerId;
        this.valueRead = DomainUtils.trim(valueRead);
        this.valueWritten = DomainUtils.trim(valueWritten);
        this.deleted = this.valueWritten != null && this.valueWritten.length == 0;
    }

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
        private long ownerId;
    }
}
