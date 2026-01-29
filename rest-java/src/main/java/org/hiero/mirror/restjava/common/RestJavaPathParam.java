// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be populated from a path variable. Similar to Spring's
 * {@link org.springframework.web.bind.annotation.PathVariable} but for fields.
 *
 * <p>Example:
 * <pre>
 * {@literal @}PathParam(name = "accountId")
 * String accountId;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestJavaPathParam {

    /**
     * The name of the path variable to bind to.
     */
    String value() default "";

    /**
     * Alias for {@link #value()}.
     */
    String name() default "";

    /**
     * Whether the path variable is required. Default is true, leading to an exception being thrown if the path variable
     * is missing.
     */
    boolean required() default true;
}
