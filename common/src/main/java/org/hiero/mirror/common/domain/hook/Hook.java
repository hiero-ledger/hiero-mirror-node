// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Entity
@IdClass(Hook.Id.class)
@NoArgsConstructor
public class Hook implements Persistable<Hook.Id> {

    @ToString.Exclude
    private byte[] adminKey;

    private EntityId contractId;

    @Column(updatable = false)
    private Long createdTimestamp;

    private boolean deleted;

    @Enumerated
    private HookExtensionPoint extensionPoint;

    @jakarta.persistence.Id
    private long hookId;

    @jakarta.persistence.Id
    private long ownerId;

    @Enumerated
    private HookType type;

    @Override
    @JsonIgnore
    public Id getId() {
        return new Id(hookId, ownerId);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    public void setOwnerId(EntityId ownerId) {
        this.ownerId = ownerId.getId();
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
}
