// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * JDBC-friendly wrapper for Postgres {@code hook_extension_point}. Do not map {@link org.postgresql.util.PGobject}
 * directly on an aggregate: Spring Data JDBC treats it as a nested object ({@code type}/{@code value} properties).
 */
@Getter
@EqualsAndHashCode
public final class PostgresHookExtensionPoint implements Serializable {

    private final HookExtensionPoint hookExtensionPoint;

    private PostgresHookExtensionPoint(HookExtensionPoint hookExtensionPoint) {
        this.hookExtensionPoint = hookExtensionPoint;
    }

    public static PostgresHookExtensionPoint of(HookExtensionPoint hookExtensionPoint) {
        return hookExtensionPoint == null ? null : new PostgresHookExtensionPoint(hookExtensionPoint);
    }
}
