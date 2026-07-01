// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hiero.mirror.web3.validation.Hex;
import org.springframework.validation.annotation.Validated;

/**
 * A single storage slot key-value pair used within a {@link StateOverride}.
 * Both {@code key} and {@code value} are 32-byte hex-encoded storage slot identifiers/values.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Validated
public class StorageEntry {

    public static final int SLOT_HEX_LENGTH = 64;

    @Hex(minLength = SLOT_HEX_LENGTH, maxLength = SLOT_HEX_LENGTH)
    @NotNull
    private String key;

    @Hex(minLength = SLOT_HEX_LENGTH, maxLength = SLOT_HEX_LENGTH)
    @NotNull
    private String value;
}
