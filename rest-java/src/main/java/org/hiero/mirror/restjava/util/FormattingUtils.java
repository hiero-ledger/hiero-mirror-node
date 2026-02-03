// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.util;

import org.hiero.mirror.rest.model.TimestampRangeNullable;

/**
 * Utility class for formatting data to match rest (Node.js) module output format.
 */
public final class FormattingUtils {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_DAY = 86_400_000_000_000L;
    private static final String HEX_PREFIX = "0x";

    private FormattingUtils() {
        // Utility class
    }

    /**
     * Converts nanoseconds since epoch to seconds.nanoseconds format. Example: 187654000123457 -> "187654.000123457"
     *
     * @param ns nanoseconds since epoch
     * @return formatted timestamp string or null if input is null
     */
    public static String nsToSecNs(Long ns) {
        if (ns == null) {
            return null;
        }

        String nsStr = String.valueOf(ns);
        if (nsStr.length() <= 9) {
            // Less than 1 second - pad with zeros
            String secs = "0";
            String nanos = String.format("%09d", ns);
            return secs + "." + nanos;
        }

        // Split into seconds and nanoseconds
        String secs = nsStr.substring(0, nsStr.length() - 9);
        String nanos = nsStr.substring(nsStr.length() - 9);

        // Ensure nanoseconds is always 9 digits (should already be, but be safe)
        if (nanos.length() < 9) {
            nanos = String.format("%09d", Long.parseLong(nanos));
        }

        return secs + "." + nanos;
    }

    /**
     * Adds "0x" prefix to hex string if not already present. If input is null or empty, returns "0x".
     *
     * @param hexData hex string or byte array
     * @return hex string with "0x" prefix
     */
    public static String addHexPrefix(String hexData) {
        if (hexData == null || hexData.isEmpty()) {
            return HEX_PREFIX;
        }

        if (hexData.startsWith(HEX_PREFIX)) {
            return hexData;
        }

        return HEX_PREFIX + hexData;
    }

    /**
     * Adds "0x" prefix to hex data from byte array.
     *
     * @param hexData byte array
     * @return hex string with "0x" prefix
     */
    public static String addHexPrefix(byte[] hexData) {
        if (hexData == null || hexData.length == 0) {
            return HEX_PREFIX;
        }

        String hexString = bytesToHex(hexData);
        return addHexPrefix(hexString);
    }

    /**
     * Converts byte array to hex string (without 0x prefix).
     *
     * @param bytes byte array
     * @return hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Gets staking period object from staking period timestamp. Matches the rest module's getStakingPeriod() function.
     *
     * @param stakingPeriod staking period start timestamp
     * @return TimestampRangeNullable with from/to, or null if input is null
     */
    public static TimestampRangeNullable getStakingPeriod(Long stakingPeriod) {
        if (stakingPeriod == null) {
            return null;
        }

        // Add 1 nanosecond to staking period start
        long stakingPeriodStart = stakingPeriod + 1L;

        var period = new TimestampRangeNullable();
        period.setFrom(nsToSecNs(stakingPeriodStart));
        period.setTo(incrementTimestampByOneDay(stakingPeriodStart));
        return period;
    }

    /**
     * Increments timestamp by one day and formats as seconds.nanoseconds.
     *
     * @param ns nanoseconds since epoch
     * @return timestamp + 1 day in seconds.nanoseconds format
     */
    private static String incrementTimestampByOneDay(long ns) {
        long incremented = ns + NANOS_PER_DAY;
        return nsToSecNs(incremented);
    }
}
