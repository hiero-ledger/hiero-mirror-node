// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.MAX_TRACE_TRANSACTIONS;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One {@code debug_traceCallMany} bundle. Transactions run sequentially, share this bundle's {@code block_override},
 * and keep state changes for later bundles. The request state override stays in place for the whole trace.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NullMarked
public class ActionTraceBundle {

    @JsonProperty("block_override")
    @Nullable
    @Valid
    private BlockOverride blockOverride;

    @NotEmpty
    @NotNull
    @Size(max = MAX_TRACE_TRANSACTIONS)
    @Valid
    private List<@NotNull @Valid ContractCallRequest> transactions = new ArrayList<>();
}
