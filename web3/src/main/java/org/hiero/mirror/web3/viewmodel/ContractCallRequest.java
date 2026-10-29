// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.web3.convert.BlockTypeSerializer;
import org.hiero.mirror.web3.utils.BytecodeUtils;
import org.hiero.mirror.web3.validation.Hex;
import org.jspecify.annotations.Nullable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContractCallRequest {

    public static final int ADDRESS_LENGTH = 40;
    public static final long DATA_MAX_LENGTH = 300_000L;
    public static final int ACCESS_LIST_MAX_SIZE = 1_000;
    public static final int AUTHORIZATION_LIST_MAX_SIZE = 1_000;

    @JsonProperty("access_list")
    @Nullable
    @Size(max = ACCESS_LIST_MAX_SIZE)
    @Valid
    private List<@NotNull AccessListEntry> accessList;

    // Validated, but not yet applied to execution.
    @JsonProperty("authorization_list")
    @Nullable
    @Size(max = AUTHORIZATION_LIST_MAX_SIZE)
    @Valid
    private List<@NotNull AuthorizationListEntry> authorizationList;

    @JsonSerialize(using = BlockTypeSerializer.class)
    @JsonSetter(nulls = Nulls.SKIP)
    @NotNull
    private BlockType block = BlockType.LATEST;

    @Hex(maxLength = DATA_MAX_LENGTH)
    private String data;

    private boolean estimate;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
    private String from;

    @Min(21_000)
    private long gas = 15_000_000L;

    @JsonAlias("gas_price")
    @Min(0)
    private long gasPrice;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH, allowEmpty = true)
    private String to;

    @JsonProperty("state_overrides")
    @NotNull
    @Size(max = 10)
    private List<@Valid StateOverride> stateOverrides = new ArrayList<>();

    @PositiveOrZero
    private long value;

    // Plain setters, as the Jackson 3 HTTP binding ignores Jackson 2's @JsonDeserialize.
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

    private long parseValue(final Object value) {
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
