// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.ADDRESS_LENGTH;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.validation.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * EVM block context override. Every field is optional, and an omitted field keeps the value of the bound record file or
 * the message frame. Every field except {@code fee_recipient} is a decimal long. {@code fee_recipient} is a 20-byte EVM
 * address. JSON names are snake_case through the global {@code spring.jackson.property-naming-strategy}.
 * {@code block_number} and {@code time} flow through the block state singletons, so the frame's block values and
 * {@code BLOCKHASH} see them too. The other fields are read by the mirror node opcode overrides.
 *
 * @param baseFeePerGas EVM {@code block.basefee} in weibar per gas
 * @param blobBaseFee   EVM {@code block.blobbasefee} in weibar per blob gas
 * @param blockNumber   EVM {@code block.number}. Also moves the range {@code BLOCKHASH} checks
 * @param feeRecipient  EVM {@code block.coinbase}. Defaults to the network fee collection account
 * @param gasLimit      EVM {@code block.gaslimit}
 * @param prevRandao    value returned by {@code PREVRANDAO}, zero-extended to 32 bytes
 * @param time          EVM {@code block.timestamp} in Unix seconds
 */
@Builder
@NullMarked
@Validated
public record BlockOverride(
        @Nullable @PositiveOrZero Long baseFeePerGas,
        @Nullable @Positive Long blobBaseFee,
        @Nullable @PositiveOrZero Long blockNumber,

        @Nullable @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
        String feeRecipient,

        @Nullable @Positive Long gasLimit,
        @Nullable @PositiveOrZero Long prevRandao,
        @Nullable @PositiveOrZero Long time) {

    public static final BlockOverride EMPTY = BlockOverride.builder().build();

    /** Value returned by {@code COINBASE}, or null to keep the frame value. */
    public @Nullable Address feeRecipientAddress() {
        return feeRecipient == null || feeRecipient.isBlank() ? null : Address.fromHexString(feeRecipient.trim());
    }

    @JsonIgnore
    public boolean isEmpty() {
        return equals(EMPTY);
    }

    /** Value returned by {@code PREVRANDAO}, or null to keep the frame value. */
    public @Nullable Bytes32 prevRandaoBytes() {
        return prevRandao == null ? null : UInt256.valueOf(prevRandao);
    }

    /** EVM {@code block.timestamp} in nanoseconds since epoch, or null to keep the record file value. */
    public @Nullable Long timeNanos() {
        return time == null ? null : DomainUtils.convertToNanosMax(time, 0L);
    }
}
