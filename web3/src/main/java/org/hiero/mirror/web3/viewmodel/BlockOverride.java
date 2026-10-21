// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    @Nullable
    private String number;

    @Nullable
    private String time;
}
