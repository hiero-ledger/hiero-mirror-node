// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.experimental.UtilityClass;

/**
 * Utility methods for transforming and calculating values related to network stake.
 */
@UtilityClass
public class NetworkStakeUtils {

    private static final int FRACTION_SCALE = 9;

    /**
     * Calculates the fractional value of a numerator and denominator as a float with up to {@value #FRACTION_SCALE} decimal places.
     *
     * @param numerator   the numerator of the fraction
     * @param denominator the denominator of the fraction
     * @return the result of numerator / denominator as a float, or 0.0f if denominator is 0
     */
    public static float mapFraction(long numerator, long denominator) {
        if (denominator == 0L) {
            return 0f;
        }

        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), FRACTION_SCALE, RoundingMode.HALF_UP)
                .floatValue();
    }
}
