// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.ACCESS_LIST_MAX_SIZE;
import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.ADDRESS_LENGTH;
import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.AUTHORIZATION_LIST_MAX_SIZE;
import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.DATA_MAX_LENGTH;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;
import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.web3.utils.BytecodeUtils;
import org.hiero.mirror.web3.validation.Hex;
import org.jspecify.annotations.Nullable;
import org.springframework.validation.annotation.Validated;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Validated
public class SimulateCall {

    // A 256-bit EVM quantity, in hexadecimal digits.
    private static final int QUANTITY_MAX_LENGTH = 64;

    // Accepted and validated per HIP-1485, not yet applied to execution (same as debug_traceCall).
    @JsonProperty("access_list")
    @Nullable
    @Size(max = ACCESS_LIST_MAX_SIZE)
    @Valid
    private List<@Valid AccessListEntry> accessList;

    @JsonProperty("authorization_list")
    @Nullable
    @Size(max = AUTHORIZATION_LIST_MAX_SIZE)
    @Valid
    private List<@Valid AuthorizationListEntry> authorizationList;

    @Hex(maxLength = DATA_MAX_LENGTH)
    private String data;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
    private String from;

    @Min(21_000)
    private long gas = 15_000_000L;

    @JsonAlias("gas_price")
    @Min(0)
    private long gasPrice;

    /**
     * Accepted for compatibility with the HIP's example request, but unused: Mirror Node has no EIP-1559 fee market.
     */
    @Hex(maxLength = QUANTITY_MAX_LENGTH)
    @JsonProperty("max_fee_per_gas")
    private String maxFeePerGas;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH, allowEmpty = true)
    private String to;

    @PositiveOrZero
    private long value;

    // Plain setters, not @JsonDeserialize: this module's HTTP binding may run on Jackson 3, which ignores it.
    public void setGas(final Object gas) {
        if (gas != null) {
            this.gas = parseValue(gas);
        }
    }

    public void setGasPrice(final Object gasPrice) {
        if (gasPrice != null) {
            this.gasPrice = parseValue(gasPrice);
        }
    }

    public void setValue(final Object value) {
        if (value != null) {
            this.value = parseValue(value);
        }
    }

    @AssertTrue(message = "must not be empty")
    private boolean hasFrom() {
        return value <= 0 || from != null;
    }

    @AssertTrue(message = "must not be empty")
    private boolean hasTo() {
        boolean isValidToField = value <= 0 || from == null || StringUtils.isNotEmpty(to);
        return BytecodeUtils.isValidInitBytecode(data) || isValidToField;
    }

    private static long parseValue(final Object value) {
        return switch (value) {
            case Integer intValue -> intValue;
            case Long longValue -> longValue;
            case BigInteger bigInteger -> bigInteger.longValueExact();
            case String text when text.regionMatches(true, 0, "0x", 0, 2) -> Long.parseLong(text.substring(2), 16);
            case String text -> Long.parseLong(text);
            default -> throw new IllegalArgumentException("expected a number or a hexadecimal/decimal string");
        };
    }
}
