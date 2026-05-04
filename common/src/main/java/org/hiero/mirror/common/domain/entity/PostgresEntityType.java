// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public final class PostgresEntityType implements Serializable {

    private final EntityType entityType;

    private PostgresEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public static PostgresEntityType of(EntityType entityType) {
        return entityType == null ? null : new PostgresEntityType(entityType);
    }
}
