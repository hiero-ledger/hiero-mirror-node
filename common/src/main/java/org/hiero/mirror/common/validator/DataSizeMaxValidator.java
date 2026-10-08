// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.unit.DataSize;

public final class DataSizeMaxValidator implements ConstraintValidator<DataSizeMax, DataSize> {

    private DataSize max;

    @Override
    public void initialize(DataSizeMax annotation) {
        max = DataSize.of(annotation.value(), annotation.unit());
    }

    @Override
    public boolean isValid(DataSize value, ConstraintValidatorContext context) {
        return value == null || value.compareTo(max) <= 0;
    }
}
