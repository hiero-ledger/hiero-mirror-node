// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.validator;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.util.unit.DataUnit;

@Documented
@Constraint(validatedBy = DataSizeMaxValidator.class)
@Target({FIELD, METHOD, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
public @interface DataSizeMax {

    Class<?>[] groups() default {};

    String message() default "must be less than or equal to {value} {unit}";

    Class<? extends Payload>[] payload() default {};

    DataUnit unit() default DataUnit.BYTES;

    long value();
}
