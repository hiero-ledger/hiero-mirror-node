// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.ADDRESS_LENGTH;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.hiero.mirror.web3.validation.Hex;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NullMarked
public class AccessListEntry {

    public static final int STORAGE_KEYS_MAX_SIZE = 10_000;

    // Unset until JSON bind. @Hex treats null as valid, so @NotNull is required.
    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
    @NotNull
    @Nullable
    private String address;

    @JsonProperty("storage_keys")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @NotNull
    @Size(max = STORAGE_KEYS_MAX_SIZE)
    private List<@Hex String> storageKeys = new ArrayList<>();
}
