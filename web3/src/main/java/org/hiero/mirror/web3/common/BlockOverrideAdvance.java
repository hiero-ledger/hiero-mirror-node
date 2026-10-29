// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.utils.HexUtils;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.jspecify.annotations.Nullable;

/**
 * Advances EVM block number and timestamp across trace bundles. Time stays in seconds; callers convert to nanoseconds
 * only when storing the value on {@link ContractCallContext}.
 */
public final class BlockOverrideAdvance {

    private BlockOverrideAdvance() {}

    /**
     * Header for the first bundle. Omitted fields take the bound record file's block number and timestamp. They are
     * not incremented, and an explicit value is not compared to the chain head.
     *
     * @param recordNumber       block number of the record file the trace is bound to
     * @param recordTimeSeconds  that record file's consensus start, in seconds
     * @param override           this bundle's override, or null when the bundle omits one
     */
    public static Cursor first(
            final long recordNumber, final long recordTimeSeconds, final @Nullable BlockOverride override) {
        final var number = override == null ? null : parse(override.getNumber(), "number");
        final var time = override == null ? null : parse(override.getTime(), "time");
        return new Cursor(number != null ? number : recordNumber, time != null ? time : recordTimeSeconds);
    }

    /**
     * @param previousNumber       block number left by the previous bundle, or null when unset
     * @param previousTimeSeconds  block timestamp in seconds left by the previous bundle, or null when unset
     * @param override             this bundle's override, or null when the bundle omits one
     * @param bundleIndex          index of this bundle, used in the older-value error
     */
    public static Cursor next(
            final @Nullable Long previousNumber,
            final @Nullable Long previousTimeSeconds,
            final @Nullable BlockOverride override,
            final int bundleIndex) {
        if (override == null) {
            return new Cursor(advance(previousNumber, 1L), advance(previousTimeSeconds, 2L));
        }
        final var number = parse(override.getNumber(), "number");
        final var time = parse(override.getTime(), "time");
        if (number != null && previousNumber != null && number < previousNumber) {
            throw older(bundleIndex, "number");
        }
        if (time != null && previousTimeSeconds != null && time < previousTimeSeconds) {
            throw older(bundleIndex, "time");
        }
        return new Cursor(
                number != null ? number : advance(previousNumber, 1L),
                time != null ? time : advance(previousTimeSeconds, 2L));
    }

    private static @Nullable Long advance(final @Nullable Long value, final long step) {
        if (value == null) {
            return null;
        }
        try {
            return Math.addExact(value, step);
        } catch (final ArithmeticException _) {
            throw new InvalidParametersException("Invalid block_override: block context overflow");
        }
    }

    private static InvalidParametersException older(final int bundleIndex, final String field) {
        return new InvalidParametersException("block_override.%s on bundle %d is older than bundle %d"
                .formatted(field, bundleIndex, bundleIndex - 1));
    }

    private static @Nullable Long parse(final @Nullable String value, final String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return HexUtils.parseValue(value);
        } catch (final NumberFormatException _) {
            throw new InvalidParametersException("Invalid block_override." + field);
        }
    }

    public record Cursor(@Nullable Long number, @Nullable Long timeSeconds) {}
}
