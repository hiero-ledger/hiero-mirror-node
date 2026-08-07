// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

/**
 * One entry of {@link SimulateRequest#getBlockStateCalls()}: a virtual block of cumulative calls plus the
 * {@link StateOverride}s that take effect when it begins. Unlike the calls, overrides persist into later entries.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Validated
public class SimulateBlockStateCall {

    @NotNull
    private List<@NotNull @Valid SimulateCall> calls = List.of();

    @JsonProperty("state_overrides")
    @NotNull
    private List<@NotNull @Valid StateOverride> stateOverrides = List.of();
}
