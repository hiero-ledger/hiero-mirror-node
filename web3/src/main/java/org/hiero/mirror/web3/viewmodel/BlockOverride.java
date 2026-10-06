// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Optional EVM block context override. {@code number} and {@code time} cannot be populated simultaneously.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NullMarked
public class BlockOverride {

    @Nullable
    private String number;

    @Nullable
    private String time;

    @AssertTrue(message = "number and time cannot be populated simultaneously")
    private boolean hasExclusiveNumberAndTime() {
        return StringUtils.isBlank(number) || StringUtils.isBlank(time);
    }
}
