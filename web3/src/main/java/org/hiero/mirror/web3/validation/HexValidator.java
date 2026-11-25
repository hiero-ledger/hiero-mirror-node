// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class HexValidator implements ConstraintValidator<Hex, String> {

    public static final String MESSAGE = "invalid hexadecimal string";
    public static final String HEX_PREFIX = "0x";
    public static final String HEX_PREFIX_CAPITAL = "0X";

    private boolean allowEmpty;
    private long minLength;
    private Pattern pattern;

    @Override
    public void initialize(Hex hex) {
        if (hex.minLength() < 0 || hex.maxLength() < hex.minLength()) {
            throw new IllegalArgumentException(
                    "invalid @Hex bounds: [%d, %d]".formatted(hex.minLength(), hex.maxLength()));
        }

        allowEmpty = hex.allowEmpty();
        minLength = hex.minLength();
        pattern = Pattern.compile("^(0[xX])?[0-9a-fA-F]{%d,%d}$".formatted(minLength, hex.maxLength()));
    }

    /** A valid value encodes whole bytes, so it has an even number of hex digits after the optional prefix. */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || ((minLength == 0 || allowEmpty) && value.isEmpty())) {
            return true;
        }

        // The prefix has two characters, so the digit count has the same parity as the whole value.
        return (value.length() & 1) == 0 && pattern.matcher(value).matches();
    }
}
