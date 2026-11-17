// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.ADDRESS_LENGTH;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.hiero.mirror.web3.convert.StrictLongDeserializer;
import org.hiero.mirror.web3.validation.Hex;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * EVM block context override. Every field is optional, and an omitted field keeps its normal value. Every field except
 * {@code fee_recipient} is a decimal long. {@code fee_recipient} is a 20-byte EVM address.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NullMarked
public class BlockOverride {

    /** EVM {@code block.basefee} in weibar per gas. */
    @Nullable
    @PositiveOrZero
    @JsonDeserialize(using = StrictLongDeserializer.class)
    private Long baseFeePerGas;

    /** EVM {@code block.blobbasefee} in weibar per blob gas. */
    @Nullable
    @Positive
    @JsonDeserialize(using = StrictLongDeserializer.class)
    private Long blobBaseFee;

    /** EVM {@code block.number}. Also moves the range {@code BLOCKHASH} checks. */
    @Nullable
    @PositiveOrZero
    @JsonDeserialize(using = StrictLongDeserializer.class)
    private Long blockNumber;

    /** EVM {@code block.coinbase}. Defaults to the network fee collection account. */
    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
    @Nullable
    private String feeRecipient;

    /** EVM {@code block.gaslimit}. */
    @Nullable
    @Positive
    @JsonDeserialize(using = StrictLongDeserializer.class)
    private Long gasLimit;

    /** Value returned by {@code PREVRANDAO}, zero-extended to 32 bytes. */
    @Nullable
    @PositiveOrZero
    @JsonDeserialize(using = StrictLongDeserializer.class)
    private Long prevRandao;

    /** EVM {@code block.timestamp} in Unix seconds. */
    @Nullable
    @PositiveOrZero
    @JsonDeserialize(using = StrictLongDeserializer.class)
    private Long time;
}
