// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.MAX_TRACE_TRANSACTIONS;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.NullMarked;

/**
 * One {@code debug_traceCallMany} bundle. Transactions run sequentially and keep state changes for later bundles. The
 * request state override stays in place for the whole trace, while the block override applies to this bundle only.
 */
@Data
@NullMarked
public class ActionTraceBundle {

    /** EVM block context for every transaction of this bundle. {@link BlockOverride#EMPTY} keeps the request block. */
    @JsonSetter(nulls = Nulls.SKIP)
    @NotNull
    @Valid
    private BlockOverride blockOverride = BlockOverride.EMPTY;

    /** Accepted only so the controller can reject it. State overrides belong on {@link ActionTraceRequest}. */
    @JsonSetter(nulls = Nulls.SKIP)
    private List<StateOverride> stateOverrides = List.of();

    @NotNull
    @Size(min = 1, max = MAX_TRACE_TRANSACTIONS)
    @Valid
    private List<@NotNull @Valid ContractCallRequest> transactions = new ArrayList<>();
}
