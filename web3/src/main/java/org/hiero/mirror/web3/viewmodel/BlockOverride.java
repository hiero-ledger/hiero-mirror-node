// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Optional EVM block context override. {@code number} and {@code time} may both be set.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NullMarked
public class BlockOverride {

    /** Decimal signed long, or {@code 0x} plus 16 hex digits. */
    public static final int MAX_LENGTH = 19;

    @Nullable
    @Size(max = MAX_LENGTH)
    private String number;

    @Nullable
    @Size(max = MAX_LENGTH)
    private String time;
}
