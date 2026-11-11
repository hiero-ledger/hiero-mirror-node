// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.validation.annotation.Validated;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Validated
public class SimulateBlockStateCall {

    @JsonProperty("block_override")
    @Nullable
    @Valid
    private BlockOverride blockOverride;

    @NotNull
    @Valid
    private List<@NotNull ContractCallRequest> calls = List.of();

    @JsonProperty("state_overrides")
    @NotNull
    @Size(max = 10)
    @Valid
    private List<@NotNull StateOverride> stateOverrides = List.of();

    @AssertTrue(message = "state_overrides are only supported on the entry, not on a call")
    private boolean hasNoCallStateOverrides() {
        if (calls == null) {
            return true;
        }
        for (final var call : calls) {
            if (call != null && !call.getStateOverrides().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
