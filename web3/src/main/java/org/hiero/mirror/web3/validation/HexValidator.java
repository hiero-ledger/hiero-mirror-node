// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class HexValidator implements ConstraintValidator<Hex, String> {

    public static final String MESSAGE = "invalid hexadecimal string";
    public static final String HEX_PREFIX = "0x";

    private boolean allowEmpty;
    private long minLength;
    private Pattern pattern;

    @Override
    public void initialize(Hex hex) {
        allowEmpty = hex.allowEmpty();
        minLength = hex.minLength();
        pattern = Pattern.compile("^(0x)?[0-9a-fA-F]{%d,%d}$".formatted(minLength, hex.maxLength()));
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || ((minLength == 0 || allowEmpty) && value.isEmpty())) {
            return true;
        }

        return pattern.matcher(value).matches();
    }
}
