// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class HexValidator implements ConstraintValidator<Hex, String> {

    public static final String MESSAGE = "invalid hexadecimal string";
    private static final Pattern HEX_PATTERN = Pattern.compile("^(0x)?[0-9a-fA-F]+$");
    public static final String HEX_PREFIX = "0x";

    private long maxLength;
    private long minLength;
    private boolean allowEmpty;

    @Override
    public void initialize(Hex hex) {
        maxLength = hex.maxLength();
        minLength = hex.minLength();
        allowEmpty = hex.allowEmpty();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || ((minLength == 0 || allowEmpty) && value.isEmpty())) {
            return true;
        }

        if (!HEX_PATTERN.matcher(value).matches()) {
            return false;
        }

        int prefixLength = value.startsWith(HEX_PREFIX) ? HEX_PREFIX.length() : 0;
        int length = value.length() - prefixLength;
        return length >= minLength && length <= maxLength;
    }
}
