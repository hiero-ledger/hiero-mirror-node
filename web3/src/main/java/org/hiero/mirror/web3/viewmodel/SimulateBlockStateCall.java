// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class SimulateBlockStateCall {

    public static final int MAX_CALLS = 2;

    @JsonSetter(nulls = Nulls.SKIP)
    @NotNull
    @Valid
    private BlockOverride blockOverride = BlockOverride.EMPTY;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Size(min = 1, max = MAX_CALLS)
    @Valid
    private List<@NotNull ContractCallRequest> calls = List.of();

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Size(max = 10)
    @Valid
    private List<@NotNull StateOverride> stateOverrides = List.of();

    @AssertTrue(message = "block_override is only supported on the entry, not on a call")
    private boolean hasNoCallBlockOverrides() {
        for (final var call : calls) {
            if (call != null && !call.getBlockOverride().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @AssertTrue(message = "state_overrides are only supported on the entry, not on a call")
    private boolean hasNoCallStateOverrides() {
        for (final var call : calls) {
            if (call != null && !call.getStateOverrides().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
