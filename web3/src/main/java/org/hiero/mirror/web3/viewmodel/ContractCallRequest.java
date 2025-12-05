// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.web3.convert.BlockTypeDeserializer;
import org.hiero.mirror.web3.convert.BlockTypeSerializer;
import org.hiero.mirror.web3.utils.BytecodeUtils;
import org.hiero.mirror.web3.validation.Hex;

@Data
public class ContractCallRequest {

    public static final int ADDRESS_LENGTH = 40;

    @JsonSerialize(using = BlockTypeSerializer.class)
    @JsonDeserialize(using = BlockTypeDeserializer.class)
    private BlockType block = BlockType.LATEST;

    @Hex
    private String data;

    private boolean estimate;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
    private String from;

    @Min(21_000)
    private long gas = 15_000_000L;

    @Min(0)
    private long gasPrice;

    @JsonIgnore
    private Boolean modularized;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH, allowEmpty = true)
    private String to;

    @PositiveOrZero
    private long value;

    @AssertTrue(message = "must not be empty")
    private boolean hasFrom() {
        return value <= 0 || from != null;
    }

    @AssertTrue(message = "must not be empty")
    private boolean hasTo() {
        boolean isValidToField = value <= 0 || from == null || StringUtils.isNotEmpty(to);
        return BytecodeUtils.isValidInitBytecode(data) || isValidToField;
    }
}
