// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks the domain class as using upsert logic when persisting.
 */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface Upsertable {

    /**
     * Controls whether a history entry should be added whenever this domain changes.
     *
     * @return whether history is enabled
     */
    boolean history() default false;

    /**
     * Controls whether an update transaction without an existing create transaction should be skipped or not.
     *
     * @return whether partial updates are enabled
     */
    boolean skipPartialUpdate() default false;
}
