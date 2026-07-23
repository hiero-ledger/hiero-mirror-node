// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.ADDRESS_LENGTH;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigInteger;
import lombok.Data;
import org.hiero.mirror.web3.validation.Hex;
import org.springframework.validation.annotation.Validated;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Validated
public class SimulateCall {

    @Hex
    private String data;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
    private String from;

    @Min(21_000)
    private long gas = 15_000_000L;

    @Min(0)
    private long gasPrice;

    /**
     * Accepted for compatibility with the HIP's example request, but unused: Mirror Node has no EIP-1559 fee market.
     */
    @Hex
    @JsonProperty("max_fee_per_gas")
    private String maxFeePerGas;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH, allowEmpty = true)
    private String to;

    @PositiveOrZero
    private long value;

    // Plain setters, not @JsonDeserialize: this module's HTTP binding may run on Jackson 3, which ignores it.
    public void setGas(final Object gas) {
        this.gas = parseValue(gas);
    }

    public void setGasPrice(final Object gasPrice) {
        this.gasPrice = parseValue(gasPrice);
    }

    public void setValue(final Object value) {
        this.value = parseValue(value);
    }

    private static long parseValue(final Object value) {
        return switch (value) {
            case Integer intValue -> intValue;
            case Long longValue -> longValue;
            case BigInteger bigInteger -> bigInteger.longValueExact();
            case String text when text.regionMatches(true, 0, "0x", 0, 2) -> Long.parseLong(text.substring(2), 16);
            case String text -> Long.parseLong(text);
            case null, default ->
                throw new IllegalArgumentException("expected a number or a hexadecimal/decimal string");
        };
    }
}
