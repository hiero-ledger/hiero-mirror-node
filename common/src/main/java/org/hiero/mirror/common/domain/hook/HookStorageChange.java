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
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Entity
@IdClass(HookStorageChange.Id.class)
@NoArgsConstructor
public class HookStorageChange implements Persistable<HookStorageChange.Id> {

    @jakarta.persistence.Id
    private long consensusTimestamp;

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
