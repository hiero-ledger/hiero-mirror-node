// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.web.bind.annotation.ValueConstants;

/**
 * Marks a field to be populated from a query parameter. Similar to Spring's
 * {@link org.springframework.web.bind.annotation.RequestParam} but for fields.
 *
 * <p>Example:
 * <pre>
 * {@literal @}QueryParam(name = "limit", defaultValue = "25")
 * {@literal @}Min(1) {@literal @}Max(1000)
 * int limit;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestJavaQueryParam {

    /**
     * The name of the query parameter to bind to.
     */
    String value() default "";

    /**
     * Alias for {@link #value()}.
     */
    String name() default "";

    /**
     * Whether the parameter is required. Default is true, leading to an exception being thrown if the parameter is
     * missing.
     */
    boolean required() default true;

    /**
     * The default value to use as a fallback when the request parameter is not provided or empty.
     */
    String defaultValue() default ValueConstants.DEFAULT_NONE;
}
