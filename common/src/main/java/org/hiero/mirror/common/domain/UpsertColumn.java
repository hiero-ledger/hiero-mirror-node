// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used to customize the logic used to upsert this column.
 */
@Documented
@Target(FIELD)
@Retention(RUNTIME)
public @interface UpsertColumn {

    /**
     * Specify custom logic to use for coalescing this column during the upsert process. To avoid repetition,
     * replacement variables can be used. {0} is column name and {1} is column default. t or blank is the temporary
     * table alias and e is the existing.
     *
     * @return the SQL clause
     */
    String coalesce() default "";

    /**
     * Specify if the column should coalesce with existing and default value. If false, {@link #coalesce()} is ignored.
     * The default is true.
     *
     * @return Whether to coalesce the column
     */
    boolean shouldCoalesce() default true;
}
