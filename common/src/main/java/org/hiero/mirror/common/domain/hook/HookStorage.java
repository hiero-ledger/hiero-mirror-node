// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
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
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.util.DomainUtils;

@Data
@Entity
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@IdClass(HookStorage.Id.class)
@NoArgsConstructor
@Upsertable
public class HookStorage {

    private static final int KEY_BYTE_LENGTH = 32;

    @Column(updatable = false)
    private long consensusTimestamp;

    @jakarta.persistence.Id
    private long hookId;

    @jakarta.persistence.Id
    @ToString.Exclude
    private byte[] key;

    @jakarta.persistence.Id
    private long ownerId;

    @ToString.Exclude
    private byte[] value;

    @JsonIgnore
    public HookStorage.Id getId() {
        HookStorage.Id id = new HookStorage.Id();
        id.setHookId(hookId);
        id.setKey(key);
        id.setOwnerId(ownerId);
        return id;
    }

    public void setKey(byte[] key) {
        this.key = DomainUtils.leftPadBytes(key, KEY_BYTE_LENGTH);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private long hookId;
        private byte[] key;
        private long ownerId;
    }
}
