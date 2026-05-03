// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * JDBC-friendly wrapper for Postgres {@code entity_type}. Do not map {@link org.postgresql.util.PGobject}
 * directly on an aggregate: Spring Data JDBC treats it as a nested object ({@code type}/{@code value} properties).
 */
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
