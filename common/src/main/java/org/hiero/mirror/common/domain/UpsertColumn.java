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

    /**
     * Only meaningful on an {@code @Upsertable(history = true)} entity. Marks this column as the condition for
     * whether an upsert actually versions the row: the current row is always patched in place, but a
     * {@code _history} row is only cut when this column's incoming value differs from the existing row's (and an
     * existing row is present — the first-ever write for an id never historizes, since there's nothing to archive).
     * Lets a transaction type historize conditionally instead of the all-or-nothing choice of setting
     * {@code timestampRange} or not.
     *
     * @return whether a change to this column should trigger historization
     */
    boolean historizeOnChange() default false;
}
