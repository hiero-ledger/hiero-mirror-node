// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Entity
@IdClass(Hook.Id.class)
@NoArgsConstructor
@Upsertable
public class Hook {

    @ToString.Exclude
    private byte[] adminKey;

    @Column(updatable = false)
    private EntityId contractId;

    @Column(updatable = false)
    private Long createdTimestamp;

    private boolean deleted;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(updatable = false)
    private HookExtensionPoint extensionPoint;

    @jakarta.persistence.Id
    private long hookId;

    private Long modifiedTimestamp;

    @jakarta.persistence.Id
    private long ownerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(updatable = false)
    private HookType type;

    @JsonIgnore
    public Id getId() {
        return new Id(hookId, ownerId);
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
