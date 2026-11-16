// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code block_override} values applied to the EVM. A null component keeps the value of the bound record file or the
 * message frame. {@code blockNumber} and {@code timeNanos} flow through the block state singletons, so the frame's
 * block values and {@code BLOCKHASH} see them too. The other components are read by the mirror node opcode overrides.
 *
 * @param baseFee      value returned by {@code BASEFEE}
 * @param blobBaseFee  value returned by {@code BLOBBASEFEE}
 * @param blockNumber  EVM {@code block.number}
 * @param feeRecipient value returned by {@code COINBASE}
 * @param gasLimit     value returned by {@code GASLIMIT}
 * @param prevRandao   value returned by {@code PREVRANDAO}
 * @param timeNanos    EVM {@code block.timestamp}, in nanoseconds since epoch
 */
@NullMarked
public record EvmBlockOverride(
        @Nullable Wei baseFee,
        @Nullable Wei blobBaseFee,
        @Nullable Long blockNumber,
        @Nullable Address feeRecipient,
        @Nullable Long gasLimit,
        @Nullable Bytes32 prevRandao,
        @Nullable Long timeNanos) {

    public static final EvmBlockOverride EMPTY = new EvmBlockOverride(null, null, null, null, null, null, null);

    private static final String HEX_PREFIX = "0x";
    private static final String INVALID = "Invalid block_override.";

    /**
     * Validates and converts every field of {@code override}. Bean validation rejects the same values at the
     * controller; this check covers callers that reach the service directly.
     */
    public static EvmBlockOverride from(final @Nullable BlockOverride override) {
        if (override == null) {
            return EMPTY;
        }

        final var blockNumber = requireNonNegative(override.getBlockNumber(), "block_number");
        final var timeSeconds = requireNonNegative(override.getTime(), "time");
        final var baseFee = requireNonNegative(override.getBaseFeePerGas(), "base_fee_per_gas");
        final var blobBaseFee = requirePositive(override.getBlobBaseFee(), "blob_base_fee");
        final var gasLimit = requirePositive(override.getGasLimit(), "gas_limit");
        final var prevRandao = requireNonNegative(override.getPrevRandao(), "prev_randao");

        return new EvmBlockOverride(
                baseFee == null ? null : Wei.of(baseFee),
                blobBaseFee == null ? null : Wei.of(blobBaseFee),
                blockNumber,
                address(override.getFeeRecipient()),
                gasLimit,
                prevRandao == null ? null : UInt256.valueOf(prevRandao),
                toNanos(timeSeconds));
    }

    public boolean isEmpty() {
        return equals(EMPTY);
    }

    /**
     * Returns a copy with a resolved block header. Trace bundles use it to apply the block number and timestamp
     * carried forward from the previous bundle.
     *
     * @param number      EVM block number, or null to use the bound record file's
     * @param timeSeconds EVM block timestamp in seconds, or null to use the bound record file's
     */
    public EvmBlockOverride withHeader(final @Nullable Long number, final @Nullable Long timeSeconds) {
        return new EvmBlockOverride(
                baseFee, blobBaseFee, number, feeRecipient, gasLimit, prevRandao, toNanos(timeSeconds));
    }

    private static @Nullable Address address(final @Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final var trimmed = value.trim();
        final int digits = trimmed.length() - (hasHexPrefix(trimmed) ? HEX_PREFIX.length() : 0);
        if (digits != Address.SIZE * 2) {
            throw new InvalidParametersException(INVALID + "fee_recipient");
        }
        try {
            return Address.fromHexString(trimmed);
        } catch (final IllegalArgumentException _) {
            throw new InvalidParametersException(INVALID + "fee_recipient");
        }
    }

    private static boolean hasHexPrefix(final String value) {
        return value.regionMatches(true, 0, HEX_PREFIX, 0, HEX_PREFIX.length());
    }

    private static @Nullable Long requireNonNegative(final @Nullable Long value, final String field) {
        if (value != null && value < 0L) {
            throw new InvalidParametersException(INVALID + field + " must be greater than or equal to 0");
        }
        return value;
    }

    private static @Nullable Long requirePositive(final @Nullable Long value, final String field) {
        if (value != null && value <= 0L) {
            throw new InvalidParametersException(INVALID + field + " must be greater than 0");
        }
        return value;
    }

    private static @Nullable Long toNanos(final @Nullable Long timeSeconds) {
        return timeSeconds == null ? null : DomainUtils.convertToNanosMax(timeSeconds, 0L);
    }
}
