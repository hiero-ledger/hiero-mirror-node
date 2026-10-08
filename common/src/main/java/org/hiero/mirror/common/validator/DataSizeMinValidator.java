// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.unit.DataSize;

public final class DataSizeMinValidator implements ConstraintValidator<DataSizeMin, DataSize> {

    private DataSize min;

    @Override
    public void initialize(DataSizeMin annotation) {
        min = DataSize.of(annotation.value(), annotation.unit());
    }

    @Override
    public boolean isValid(DataSize value, ConstraintValidatorContext context) {
        return value == null || value.compareTo(min) >= 0;
    }
}
