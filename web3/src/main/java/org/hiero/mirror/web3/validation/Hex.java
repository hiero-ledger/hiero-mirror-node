// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.validation;

import static org.hiero.mirror.web3.validation.HexValidator.MESSAGE;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = HexValidator.class)
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Hex {
    String message() default MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * @return maxLength the element must be less than or equal to
     */
    long maxLength() default 1048576L;

    /**
     * @return minLength the element must be greater than or equal to
     */
    long minLength() default 0L;

    /**
     * @return allowEmpty flag for empty string values
     */
    boolean allowEmpty() default false;
}
