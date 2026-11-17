// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.ADDRESS_LENGTH;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.hiero.mirror.web3.validation.Hex;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NullMarked
public class AuthorizationListEntry {

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
    @Nullable
    private String address;

    @Hex(maxLength = 64)
    @Nullable
    private String chainId;

    @Nullable
    @PositiveOrZero
    private Long nonce;

    @Hex(maxLength = 64)
    @Nullable
    private String r;

    @Hex(maxLength = 64)
    @Nullable
    private String s;

    // Lombok's getYParity() makes Jackson derive "yparity", so SNAKE_CASE alone would not map y_parity
    @JsonProperty("y_parity")
    @Hex(maxLength = 1)
    @Pattern(regexp = "^(0[xX])?[01]$")
    @Nullable
    private String yParity;
}
