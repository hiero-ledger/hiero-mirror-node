// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * JDBC-friendly wrapper for Postgres {@code hook_type}. Do not map {@link org.postgresql.util.PGobject}
 * directly on an aggregate: Spring Data JDBC treats it as a nested object ({@code type}/{@code value} properties).
 */
@Getter
@EqualsAndHashCode
public final class PostgresHookType implements Serializable {

    private final HookType hookType;

    private PostgresHookType(HookType hookType) {
        this.hookType = hookType;
    }

    public static PostgresHookType of(HookType hookType) {
        return hookType == null ? null : new PostgresHookType(hookType);
    }
}
