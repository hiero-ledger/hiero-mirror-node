// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.rest.model.TimestampRange;

/**
 * Utility methods for transforming and calculating values related to network stake.
 */
@UtilityClass
public class NetworkStakeUtils {

    private static final long SECONDS_PER_DAY = 86400L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /** Number of digits after decimal point in timestamp formatting and rounding */
    private static final int TIMESTAMP_SCALE = 9;

    /** Format string for nanosecond timestamps in "seconds.nanoseconds" format */
    private static final String TIMESTAMP_FORMAT = "%d.%0" + TIMESTAMP_SCALE + "d";

    private static final int FRACTION_SCALE = 9;

    /**
     * Calculates the fractional value of a numerator and denominator as a float with up to {@value #FRACTION_SCALE} decimal places.
     *
     * @param numerator   the numerator of the fraction
     * @param denominator the denominator of the fraction
     * @return the result of numerator / denominator as a float, or 0.0f if denominator is 0
     */
    public static float toFraction(long numerator, long denominator) {
        if (denominator == 0L) {
            return 0f;
        }

        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), FRACTION_SCALE, RoundingMode.HALF_UP)
                .floatValue();
    }

    /**
     * Converts a staking period in nanoseconds into a {@link TimestampRange} with formatted from/to strings.
     *
     * @param stakingPeriod nanoseconds since epoch
     * @return a TimestampRange object with from and to in "seconds.nanoseconds" format
     */
    public static TimestampRange toTimestampRange(long stakingPeriod) {
        long fromNs = stakingPeriod + 1;
        long toNs = fromNs + (SECONDS_PER_DAY * NANOS_PER_SECOND);

        return new TimestampRange().from(formatTimestamp(fromNs)).to(formatTimestamp(toNs));
    }

    private static String formatTimestamp(long nanos) {
        long seconds = nanos / NANOS_PER_SECOND;
        long nanoPart = nanos % NANOS_PER_SECOND;
        return String.format(TIMESTAMP_FORMAT, seconds, nanoPart);
    }
}
